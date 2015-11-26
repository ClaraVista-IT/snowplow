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

// Akka
import akka.actor.{Actor, ActorRefFactory}
import akka.util.Timeout

// Spray
import spray.http.Timedout
import spray.routing.HttpService

// Scala
import scala.concurrent.duration._

// Snowplow

// Actor accepting Http requests for the Scala collector.
class CollectorServiceActor(collectorConfig: CollectorConfig,
    sinks: CollectorSinks) extends Actor with HttpService {
  implicit val timeout: Timeout = 1.second // For the actor 'asks'
  def actorRefFactory = context

  // Deletage responses (content and storing) to the ResponseHandler.
  private val responseHandler = new ResponseHandler(collectorConfig, sinks)

  // Use CollectorService so the same route can be accessed differently
  // in the testing framework.


  /** redirectId est extraite de la classe collectorConfig. Elle est passée ensuite a la Classe CollectorService
   * */
   val redirectId = collectorConfig.redirectId.toString()

  private val collectorService = new CollectorService(responseHandler, context,Option(redirectId))

  // Message loop for the Spray service.
  def receive = handleTimeouts orElse runRoute(collectorService.collectorRoute)

  def handleTimeouts: Receive = {
    case Timedout(_) => sender ! responseHandler.timeout
  }
}

/**
 * Companion object for the CollectorService class
 */
object CollectorService {
  private val QuerystringExtractor = "^[^?]*\\?([^#]*)(?:#.*)?$".r
}

// Store the route in CollectorService to be accessed from
// both CollectorServiceActor and from the testing framework.
class CollectorService(
    responseHandler: ResponseHandler,
    context: ActorRefFactory,
                        redirectId: Option[String]) extends HttpService {
  def actorRefFactory = context

  val mapingString = redirectId.getOrElse("")


  // TODO: reduce code duplication here
  val collectorRoute = {
    post {
      path(Segment / Segment) { (path1, path2) =>
        optionalCookie("sp") { reqCookie =>
          optionalHeaderValueByName("User-Agent") { userAgent =>
            optionalHeaderValueByName("Referer") { refererURI =>
              headerValueByName("Raw-Request-URI") { rawRequest =>
                hostName { host =>
                  clientIP { ip =>
                    requestInstance{ request =>
                      entity(as[String]) { body =>
                        complete(
                          responseHandler.cookie(
                            null,
                            body,
                            reqCookie,
                            userAgent,
                            host,
                            ip,
                            request,
                            refererURI,
                            "/" + path1 + "/" + path2,
                            false,
                          None)._1
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    } ~
    get {
      path("""ice\.png""".r | "i".r) { path =>
        optionalCookie("sp") { reqCookie =>
          optionalHeaderValueByName("User-Agent") { userAgent =>
            optionalHeaderValueByName("Referer") { refererURI =>
              headerValueByName("Raw-Request-URI") { rawRequest =>
                hostName { host =>
                  clientIP { ip =>
                    requestInstance { request =>
                      parameter(mapingString ?) { tt =>
                        complete {

                          /** paramètre param contient la chaine concernant l'id retourné par Appnexus ou tout autre plateforme
                            * "&adnxs_uid={ID RETOURNE PAR APPNEXUS au tout autre plateforme de mapping}", il sera extrait et
                            * passé en paramètre à la méthode "cookie" de
                            * la classe ResponseHandler
                            */
                          val value = tt.getOrElse("")
                          val param = "&"+mapingString+"=" + value



                          responseHandler.cookie(

                            rawRequest match {
                              /** rawRequest représente la chaine formée par la requête envoyée par le Tracker
                                * Une vérification par affectation a une expression régulière est obligatoire
                                * Cette vérification permet de s'assurer du format de la requête .
                                * Dans le cas ou la requete est valide, on en enlève l'id AppNexus [ qs.replace(param,"")]
                                * et le collecteur continue son travail normalement avec une requête classique
                                * */
                              case CollectorService.QuerystringExtractor(qs) => qs.replace(param, "")
                              case _ => ""
                            },
                            null,
                            reqCookie,
                            userAgent,
                            host,
                            ip,
                            request,
                            refererURI,
                            "/" + path,
                            true,
                            redirectId)._1
                          // param
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    } ~
    get {
      path("health".r) { path =>
        complete(responseHandler.healthy)
      }
    } ~
    get {
      path(Segment / Segment) { (path1, path2) =>
        optionalCookie("sp") { reqCookie =>
          optionalHeaderValueByName("User-Agent") { userAgent =>
            optionalHeaderValueByName("Referer") { refererURI =>
              headerValueByName("Raw-Request-URI") { rawRequest =>
                hostName { host =>
                  clientIP { ip =>
                    requestInstance{ request =>
                      complete(
                        responseHandler.cookie(
                          rawRequest match {
                            case CollectorService.QuerystringExtractor(qs) => qs
                            case _ => ""
                          },
                          null,
                          reqCookie,
                          userAgent,
                          host,
                          ip,
                          request,
                          refererURI,
                          "/" + path1 + "/" + path2,
                          true,
                        None)._1
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    } ~
    options {
      requestInstance { request =>
        complete(responseHandler.preflightResponse(request))
      }
    } ~
    get {
      path("""crossdomain\.xml""".r) { path =>
        complete(responseHandler.flashCrossDomainPolicy)
      }
    } ~
    complete(responseHandler.notFound)
  }


}
