addSbtPlugin("ch.epfl.scala"  % "sbt-bloop"           % "2.0.5")
addSbtPlugin("org.scalameta"  % "sbt-scalafmt"        % "2.5.2")
addSbtPlugin("ch.epfl.scala"  % "sbt-scalafix"        % "0.13.0")
addSbtPlugin("org.scalameta"  % "sbt-native-image"    % "0.3.4")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.10.4")

libraryDependencies ++= Seq(
  "io.circe"        %% "circe-parser"     % "0.14.1",
  "org.eclipse.jgit" % "org.eclipse.jgit" % "6.9.0.202403050737-r"
)
