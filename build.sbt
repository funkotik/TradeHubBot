name := "TradeHubBot"

version := "1.0"

lazy val `tradehubbot` = (project in file(".")).enablePlugins(PlayScala)

routesGenerator := InjectedRoutesGenerator

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(jdbc, cache, ws, specs2 % Test,
  "com.github.pengrad" % "java-telegram-bot-api" % "2.3.1.1")

unmanagedResourceDirectories in Test <+= baseDirectory(_ / "target/web/public/test")

resolvers ++= Seq("scalaz-bintray" at "https://dl.bintray.com/scalaz/releases")

