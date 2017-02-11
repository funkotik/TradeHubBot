name := "TradeHubBot"

version := "1.0"

lazy val `tradehubbot` = (project in file(".")).enablePlugins(PlayScala)

routesGenerator := InjectedRoutesGenerator

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(jdbc, cache, ws, specs2 % Test,
  "org.json4s" %% "json4s-jackson" % "3.3.0",
  "org.json4s" %% "json4s-ext" % "3.3.0",
  "org.json4s" %% "json4s-native" % "3.3.0")

unmanagedResourceDirectories in Test <+= baseDirectory(_ / "target/web/public/test")

resolvers ++= Seq("scalaz-bintray" at "https://dl.bintray.com/scalaz/releases")

