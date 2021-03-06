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

// Akka and Spray
import akka.actor.{ActorSystem, Props}
import akka.io.IO
import spray.can.Http

// Java
import java.io.File

// Argot
import org.clapper.argot._

// Config
import com.typesafe.config.{Config, ConfigException, ConfigFactory}

// Logging
import org.slf4j.LoggerFactory

// Snowplow
import com.snowplowanalytics.snowplow.collectors.scalastream.sinks._

// Main entry point of the Scala collector.
object ScalaCollector extends App with CamelSslConfiguration{
  lazy val log = LoggerFactory.getLogger(getClass()) // Argument specifications

  val parser = new ArgotParser(
    programName = generated.Settings.name,
    compactUsage = true,
    preUsage = Some("%s: Version %s. Copyright (c) 2015, %s.".format(
      generated.Settings.name,
      generated.Settings.version,
      generated.Settings.organization)
    )
  )



  // Mandatory config argument
  val config = parser.option[Config](List("config"), "filename",
    "Configuration file.") { (c, opt) =>
    val file = new File(c)
    if (file.exists) {
      ConfigFactory.parseFile(file)
    } else {
      parser.usage("Configuration file \"%s\" does not exist".format(c))
      ConfigFactory.empty()
    }
  }

  parser.parse(args)


  /** Load configuration file*/
  val rawConf = config.value.getOrElse(throw new RuntimeException("--config option must be provided"))
  val collectorConfig = new CollectorConfig(rawConf)




  implicit val system = ActorSystem.create("scala-stream-collector", rawConf)

  /** If the Sink is set to Kinesis, we MUST provide 3 kinesis streams
    *  for goodRecords,badRecords and mapingID*/

    val sinks = collectorConfig.sinkEnabled match {
    case Sink.Kinesis => {
      val good = KinesisSink.createAndInitialize(collectorConfig, InputType.Good)
      val bad  = KinesisSink.createAndInitialize(collectorConfig, InputType.Bad)
      val map  = KinesisSink.createAndInitialize(collectorConfig, InputType.Map)


      CollectorSinks(good, bad,map)
    }
    case Sink.Stdout  => {
      val good = new StdoutSink(InputType.Good)
      val bad = new StdoutSink(InputType.Bad)
      val map = new StdoutSink(InputType.Map)

      CollectorSinks(good, bad,map)
    }
  }

  // The handler actor replies to incoming HttpRequests.
  val handler = system.actorOf(
    Props(classOf[CollectorServiceActor], collectorConfig, sinks),
    name = "handler"
  )

  IO(Http) ! Http.Bind(handler,
    interface=collectorConfig.interface, port=collectorConfig.port)


}

// Return Options from the configuration.
object Helper {
  implicit class RichConfig(val underlying: Config) extends AnyVal {
    def getOptionalString(path: String): Option[String] = try {
      Some(underlying.getString(path))
    } catch {
      case e: ConfigException.Missing => None
    }
  }
}

// Instead of comparing strings and validating every time
// the sink is accessed, validate the string here and
// store this enumeration.
object Sink extends Enumeration {
  type Sink = Value
  val Kinesis, Stdout, Test = Value
}

// Rigidly load the configuration file here to error when
// the collector process starts rather than later.
class CollectorConfig(config: Config) {
  import Helper.RichConfig

  private val collector = config.getConfig("collector")
  val interface = collector.getString("interface")
  val port = collector.getInt("port")
  val production = collector.getBoolean("production")

  private val p3p = collector.getConfig("p3p")
  val p3pPolicyRef = p3p.getString("policyref")
  val p3pCP = p3p.getString("CP")

  private val cookie = collector.getConfig("cookie")
  val cookieExpiration = cookie.getMilliseconds("expiration")
  val cookieEnabled = cookieExpiration != 0
  val cookieDomain = cookie.getOptionalString("domain")

  private val sink = collector.getConfig("sink")

  // TODO: either change this to ADTs or switch to withName generation
  val sinkEnabled = sink.getString("enabled") match {
    case "kinesis" => Sink.Kinesis
    case "stdout" => Sink.Stdout
    case "test" => Sink.Test
    case _ => throw new RuntimeException("collector.sink.enabled unknown.")
  }

  private val kinesis = sink.getConfig("kinesis")
  private val aws = kinesis.getConfig("aws")
  val awsAccessKey = aws.getString("access-key")
  val awsSecretKey = aws.getString("secret-key")
  private val stream = kinesis.getConfig("stream")
  val streamGoodName = stream.getString("good")
  val streamBadName = stream.getString("bad")
  val streamMapName = stream.getString("map")

  private val streamRegion = stream.getString("region")
  val streamEndpoint = s"https://kinesis.${streamRegion}.amazonaws.com"

  val threadpoolSize = kinesis.hasPath("thread-pool-size") match {
    case true => kinesis.getInt("thread-pool-size")
    case _ => 10
  }

  val buffer = kinesis.getConfig("buffer")
  val byteLimit = buffer.getInt("byte-limit")
  val recordLimit = buffer.getInt("record-limit")
  val timeLimit = buffer.getInt("time-limit")

  val backoffPolicy = kinesis.getConfig("backoffPolicy")
  val minBackoff = backoffPolicy.getLong("minBackoff")
  val maxBackoff = backoffPolicy.getLong("maxBackoff")
  //parametrage supplementaire pour activer/désactiver le redirect

    /** #################################
     * REDIRECT CONF
     *#################################*/

  val redirect = collector.getConfig("redirect")
  val allowRedirect = redirect.getBoolean("allow-redirect")

  val pathToRedirect= allowRedirect match{
    case (true) => redirect.getString("path-to-redirect")
    case (false) => ""
  }

  val redirectId= allowRedirect match{
    case true => redirect.getString("redirect-id")
    case false => None
  }


  /** #################################
    * SSL + CERTIFICATE CONF
    *#################################*/
  val sprayCanConf = config.getConfig("spray.can.server")
  val sslEncryption = sprayCanConf.getString("ssl-encryption")

  val certificate= collector.getConfig("certificate")

  val pathToCertificate= sslEncryption match{
    case "on" => certificate.getString("path-to-certificate")
    case "off" => ""
  }

  val keystorePassword = sslEncryption match{
    case "on" => certificate.getString("keystore-password")
    case "off" => ""}


  val filePassword = sslEncryption match{
    case "on" => certificate.getString("file-password")
    case "off" => ""}
}