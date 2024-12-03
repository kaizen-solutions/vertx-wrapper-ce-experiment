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
  .enablePlugins(NativeImagePlugin)
  .enablePlugins(JavaAppPackaging)
  .settings(
    Compile / mainClass := Some("com.user.land.Main")
  )
  .settings(
    Compile / resourceGenerators += Def.task {
      import NativeImageGenerateMetadataFiles._
      implicit val logger: sbt.util.Logger = sbt.Keys.streams.value.log
      generateResourceFiles(
        // Path needed for cloning the metadata repository
        (Compile / target).value,
        // Path where the metadata files will be generated
        (Compile / resourceManaged).value / "META-INF" / "native-image",
        // List all tranzitive dependencies (can also add our own files)
        update.value.allModules
          .map(m => Artefact(s"${m.organization}:${m.name}:${m.revision}"))
          .toList
      )
    }.taskValue,
    nativeImageOptions += "--initialize-at-run-time=io.netty.handler.ssl.BouncyCastleAlpnSslUtils"
  )
  .settings(
    nativeImageJvmIndex := "cs",
    nativeImageJvm      := "graalvm-java23",
    nativeImageVersion  := "23.0.1"
  )
  .settings(
    name         := "tusk",
    version      := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= {
      val (vertx, vertxV) = "io.vertx" -> "4.5.11"
      Seq(
        "org.typelevel"   %% "cats-effect"         % "3.5.7",
        "co.fs2"          %% "fs2-core"            % "3.11.0",
        "org.typelevel"   %% "shapeless3-deriving" % "3.4.0",
        "io.circe"        %% "circe-parser"        % "0.14.10",
        vertx              % "vertx-sql-client"    % vertxV,
        vertx              % "vertx-pg-client"     % vertxV,
        "com.ongres.scram" % "client"              % "2.1"
//        "io.netty"         % "netty-all"           % "4.1.115.Final" % Runtime
      )
    }
  )
