name := "TradeHubBot"

version := "1.0"

lazy val `tradehubbot` = (project in file(".")).enablePlugins(PlayScala)

routesGenerator := InjectedRoutesGenerator

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(cache, ws, specs2 % Test,
//  evolutions,
  "org.json4s" %% "json4s-jackson" % "3.3.0",
  "org.json4s" %% "json4s-ext" % "3.3.0",
  "org.json4s" %% "json4s-native" % "3.3.0",
  "com.typesafe.play" %% "play-slick" % "2.0.0",
  "com.typesafe.slick" %% "slick-codegen" % "3.1.1",
//  "com.typesafe.play" %% "play-slick-evolutions" % "2.0.0",
  "org.postgresql" % "postgresql" % "9.4.1208"
)

unmanagedResourceDirectories in Test <+= baseDirectory(_ / "target/web/public/test")

resolvers ++= Seq("scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
  "Typesafe private" at "https://private-repo.typesafe.com/typesafe/maven-releases")