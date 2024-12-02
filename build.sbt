val scala3Version = "3.3.4"

inThisBuild(
  List(
    scalaVersion      := scala3Version,
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalacOptions ++= Seq("-Wunused:imports")
  )
)

lazy val root = project
  .in(file("."))
  .settings(
    name         := "tusk",
    version      := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= {
      val (vertx, vertxV) = "io.vertx" -> "4.5.11"
      Seq(
        "co.fs2"          %% "fs2-core"            % "3.11.0",
        "org.typelevel"   %% "shapeless3-deriving" % "3.4.0",
        "io.circe"        %% "circe-parser"        % "0.14.10",
        vertx              % "vertx-sql-client"    % vertxV,
        vertx              % "vertx-pg-client"     % vertxV,
        "com.ongres.scram" % "client"              % "2.1"
      )
    }
  )
