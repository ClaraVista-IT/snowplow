/* 
 * Copyright (c) 2012-2014 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
import sbt._
import Keys._

object SnowplowHadoopEnrichBuild extends Build {

  import Dependencies._
  import BuildSettings._

  // Configure prompt to show current project
  override lazy val settings = super.settings :+ {
    shellPrompt := { s => Project.extract(s).currentProject.id + " > " }
  }

  // Define our project, with basic project information and library dependencies
  lazy val project = Project("snowplow-hadoop-enrich", file("."))
    .settings(buildSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        // Java
        Libraries.hadoopCommon,
        Libraries.hadoopClientCore,
        Libraries.cascadingCore,
        Libraries.cascadingLocal,
        Libraries.cascadingHadoop,
        // Scala
        Libraries.scaldingCore,
        Libraries.scaldingArgs,
        Libraries.scaldingCommons,
        Libraries.scaldingJson,
        Libraries.scalaz7,
        Libraries.commonEnrich,
        // Scala (test only)
        Libraries.specs2,
        Libraries.scalazSpecs2,
        Libraries.compress,
        Libraries.commonsCodec,
        // Thrift (test only)
        Libraries.snowplowRawEvent,
        Libraries.collectorPayload
      )
    )
}
