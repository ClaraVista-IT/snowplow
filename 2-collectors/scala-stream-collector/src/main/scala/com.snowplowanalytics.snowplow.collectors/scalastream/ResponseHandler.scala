/*
 * Copyright (c) 2013-2014 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0, and
 * you may not use this file except in compliance with the Apache License
 * Version 2.0.  You may obtain a copy of the Apache License Version 2.0 at
 * http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Apache License Version 2.0 is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the Apache License Version 2.0 for the specific language
 * governing permissions and limitations there under.
 */
package com.snowplowanalytics.snowplow
package collectors
package scalastream

// Java
import java.util.UUID

// Apache Commons
import org.apache.commons.codec.binary.Base64

// Spray
import spray.http.HttpHeaders.{Location, RawHeader, `Access-Control-Allow-Credentials`, `Access-Control-Allow-Headers`, `Access-Control-Allow-Origin`, `Content-Type`, `Origin`, `Raw-Request-URI`, `Remote-Address`, `Set-Cookie`}
import spray.http.MediaTypes.`image/gif`
import spray.http.{AllOrigins, ContentType, DateTime, HttpCharsets, HttpCookie, HttpEntity, HttpRequest, HttpResponse, MediaTypes, RemoteAddress, SomeOrigins}

// Akka
import akka.actor.ActorRefFactory

// Typesafe config

// Java conversions
import scala.collection.JavaConversions._

// Snowplow
import com.snowplowanalytics.snowplow.CollectorPayload.thrift.model1.CollectorPayload
import com.snowplowanalytics.snowplow.collectors.scalastream.sinks._
import com.snowplowanalytics.snowplow.collectors.scalastream.utils.SplitBatch

// Contains an invisible pixel to return for `/i` requests.
object ResponseHandler {
  val pixel = Base64.decodeBase64(
    "R0lGODlhAQABAPAAAP///wAAACH5BAEAAAAALAAAAAABAAEAAAICRAEAOw=="
  )
}

// Receive requests and store data into an output sink.
class ResponseHandler(config: CollectorConfig, sinks: CollectorSinks)(implicit context: ActorRefFactory) {

  val Collector = s"${generated.Settings.shortName}-${generated.Settings.version}-" + config.sinkEnabled.toString.toLowerCase


    /**
     * Cette Methode est utilisée a chaque demande de pixel, une petite modification nous a permis de lui ajouter un paramètre
     * qui représente l'identifiant retourné par la platforme de maping (s'il existe)
     */


  // When `/i` is requested, this is called and stores an event in the
  // Kinisis sink and returns an invisible pixel with a cookie.
  def cookie(queryParams: String, body: String, requestCookie: Option[HttpCookie],
      userAgent: Option[String], hostname: String, ip: RemoteAddress,
      request: HttpRequest, refererUri: Option[String], path: String, pixelExpected: Boolean,mapId: Option[String]):
      (HttpResponse, List[Array[Byte]]) = {

    if (KinesisSink.shuttingDown) {
      (notFound, null)
    } else {

      // Make a Tuple2 with the ip address and the shard partition key
      val ipKey = ip.toOption.map(_.getHostAddress) match {
        case None => ("unknown", UUID.randomUUID.toString)
        case Some(ip) => (ip, ip)
      }

      // Use the same UUID if the request cookie contains `sp`.
      val networkUserId: String = requestCookie match {
        case Some(rc) => rc.content
        case None => UUID.randomUUID.toString
      }

      // Construct an event object from the request.
      val timestamp: Long = System.currentTimeMillis

      val event = new CollectorPayload(
        "iglu:com.snowplowanalytics.snowplow/CollectorPayload/thrift/1-0-0",
        ipKey._1,
        timestamp,
        "UTF-8",
        Collector
      )

      event.path = path
      event.querystring = queryParams
      event.body = body
      event.hostname = hostname
      event.networkUserId = networkUserId

      /** Une vérification de la taille "existance" de l'id appNexus
        * S'il existe, on appelle la méthode matchId(String,String,Long) qui s'occupe de l'écriture du Tuple dans un Buffer */

      val app =mapId.getOrElse("")
      if(app.compareTo("None") != 0){
        matchId(app, networkUserId, timestamp)}



      userAgent.foreach(event.userAgent = _)
      refererUri.foreach(event.refererUri = _)
      event.headers = request.headers.flatMap {
        case _: `Remote-Address` | _: `Raw-Request-URI` => None
        case other => Some(other.toString)
      }

      // Set the content type
      request.headers.find(_ match { case `Content-Type`(ct) => true; case _ => false }) foreach {

        // toLowerCase called because Spray seems to convert "utf" to "UTF"
        ct => event.contentType = ct.value.toLowerCase
      }

      // Split events into Good and Bad
      val eventSplit = SplitBatch.splitAndSerializePayload(event, sinks.good.MaxBytes)

      // Send events to respective sinks
      val sinkResponseGood = sinks.good.storeRawEvents(eventSplit.good, ipKey._2)
      val sinkResponseBad = sinks.bad.storeRawEvents(eventSplit.bad, ipKey._2)
      //val sinkResponseMap  =


      // Sink Responses for Test Sink
      val sinkResponse = sinkResponseGood ++ sinkResponseBad

      val policyRef = config.p3pPolicyRef
      val CP = config.p3pCP

      val headersWithoutCookie = List(
        RawHeader("P3P", "policyref=\"%s\", CP=\"%s\"".format(policyRef, CP)),
        getAccessControlAllowOriginHeader(request),
        `Access-Control-Allow-Credentials`(true)
      );
      val responseCookie = HttpCookie(
        "sp", networkUserId,
        expires = Some(DateTime.now + config.cookieExpiration),
        domain = config.cookieDomain
      );



      // Hearders  (with redirect)

      val headers = if (config.allowRedirect)  {
        List(
          RawHeader("P3P", "policyref=\"%s\", CP=\"%s\"".format(policyRef, CP)),
          `Set-Cookie`(responseCookie),
          Location(config.pathToRedirect)
       )}
        else {List(
          RawHeader("P3P", "policyref=\"%s\", CP=\"%s\"".format(policyRef, CP)),
          `Set-Cookie`(responseCookie)
        )

      }


    /**
     * Une vérification de l'activation de la fonctionnalité de REDIRECT (a travers allow-redirect dans le fichier de configuration)
     * permet de determiner le type de la réponse HTTP.
     * En effet, le status de la réponse peut poser problème sur certains navigateurs comme IE .
     * (Redirect ==> 302) (Direct response ==> 200)
     * */


      val httpResponse = if (pixelExpected) {

        if (config.allowRedirect)
          // en cas de redirect
            HttpResponse(status = 302, entity = HttpEntity(`image/gif`, ResponseHandler.pixel)).withHeaders(headers)
          else
            HttpResponse(status = 200, entity = HttpEntity(`image/gif`, ResponseHandler.pixel)).withHeaders(headers)
      }
      else {
        HttpResponse(status = 200).withHeaders(headers)
      }
      (httpResponse, sinkResponse)
    }
  }


  /**
   * Cette méthode s'occupe du stockage des matchings dans un buffer, avant qu'il soit lancé sur un stream kinesis
   * un objet IdMapper offre les fonctionnalitées d'ajout,d'envoi et de vidage de ce buffer.
   * */

  def matchId(idAppNexus: String, idClaravista: String, timestamp: Long) {
    val dateTime = DateTime(timestamp)
    val res= IdMapper.addToBuffer((idAppNexus+";"+idClaravista+";"+dateTime+"\n").getBytes())
   if(res.length >= 500 ){
     sinks.map.storeRawEvents(res, timestamp.toString)
     IdMapper.flushBuffer()}
    //bw.close()

  }


  /**
   * Creates a response to the CORS preflight Options request
   *
   * @param request Incoming preflight Options request
   * @return Response granting permissions to make the actual request
   */
  def preflightResponse(request: HttpRequest) = HttpResponse().withHeaders(List(
    getAccessControlAllowOriginHeader(request),
    `Access-Control-Allow-Credentials`(true),
    `Access-Control-Allow-Headers`( "Content-Type")))

  def flashCrossDomainPolicy = HttpEntity(
    contentType = ContentType(MediaTypes.`text/xml`, HttpCharsets.`ISO-8859-1`),
    string = "<?xml version=\"1.0\"?>\n<cross-domain-policy>\n  <allow-access-from domain=\"*\" secure=\"false\" />\n</cross-domain-policy>"
  )

  def healthy = HttpResponse(status = 200, entity = s"OK")
  def notFound = HttpResponse(status = 404, entity = "ab5.claratracking.fr : 404 Not found ! ")
  def timeout = HttpResponse(status = 500, entity = s"Request timed out.")

  /**
   * Creates an Access-Control-Allow-Origin header which specifically
   * allows the domain which made the request
   *
   * @param request Incoming request
   * @return Header
   */
  private def getAccessControlAllowOriginHeader(request: HttpRequest) =
    `Access-Control-Allow-Origin`(request.headers.find(_ match {
      case `Origin`(origin) => true
      case _ => false
    }) match {
      case Some(`Origin`(origin)) => SomeOrigins(origin)
      case _ => AllOrigins
    })
}
