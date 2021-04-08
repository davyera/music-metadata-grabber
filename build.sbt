name := "music-metadata-grabber"

version := "0.1"

scalaVersion := "2.13.5"

val akkaVersion         = "2.5.26"
val akkaHttpVersion     = "10.1.11"
val circeVersion        = "0.13.0"
val configVersion       = "1.4.1"
val jodaTimeVersion     = "2.10.10"
val jsoupVersion        = "1.13.1"
val logbackVersion      = "1.2.3"
val playVersion         = "4.0.0"
val scalajVersion       = "2.4.2"
val scalaLoggingVersion = "3.9.3"
val sprayJsonVersion    = "1.3.6"
val sttpVersion         = "2.2.9"

val mockitoCoreVersion  = "3.8.0"
val scalatestVersion    = "3.0.8"

libraryDependencies ++= Seq(
  "ch.qos.logback"                %   "logback-classic"                   % logbackVersion,
  "com.softwaremill.sttp.client"  %%  "core"                              % sttpVersion,
  "com.softwaremill.sttp.client"  %%  "circe"                             % sttpVersion,
  "com.softwaremill.sttp.client"  %%  "async-http-client-backend-future"  % sttpVersion,
  "com.typesafe.scala-logging"    %%  "scala-logging"                     % scalaLoggingVersion,
  "com.typesafe"                  %   "config"                            % configVersion,
  "io.circe"                      %%  "circe-core"                        % circeVersion,
  "io.circe"                      %%  "circe-parser"                      % circeVersion,
  "io.circe"                      %%  "circe-generic"                     % circeVersion,
  "io.circe"                      %%  "circe-generic-extras"              % circeVersion,
  "joda-time"                     %   "joda-time"                         % jodaTimeVersion,
  "org.jsoup"                     %   "jsoup"                             % jsoupVersion,

  "org.mockito"                   %   "mockito-core"                      % mockitoCoreVersion  % Test,
  "org.scalatest"                 %%  "scalatest"                         % scalatestVersion    % Test
)