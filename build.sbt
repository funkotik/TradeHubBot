import ScalaxbKeys.{packageName => scalabxPackageName, _}

name := "TradeHubBot"

version := "1.0"

//lazy val `tradehubbot` = (project in file(".")).enablePlugins(PlayScala)

routesGenerator := InjectedRoutesGenerator

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(cache, ws, specs2 % Test,
  //  evolutions,
  "org.json4s" %% "json4s-jackson" % "3.3.0",
  "org.json4s" %% "json4s-ext" % "3.3.0",
  "org.json4s" %% "json4s-native" % "3.3.0",
  "com.typesafe.play" %% "play-slick" % "2.0.0",
  "com.typesafe.slick" %% "slick-codegen" % "3.1.1",
    "com.typesafe.play" %% "play-slick-evolutions" % "2.0.0",
  "org.postgresql" % "postgresql" % "9.4.1208",
  "co.theasi" %% "plotly" % "0.2.0"
)

unmanagedResourceDirectories in Test <+= baseDirectory(_ / "target/web/public/test")

resolvers ++= Seq("scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
  "Typesafe private" at "https://private-repo.typesafe.com/typesafe/maven-releases")



lazy val commonSettings = Seq(
  organization := "com.dn",
  scalaVersion := "2.11.5"
)

lazy val scalaXml = "org.scala-lang.modules" %% "scala-xml" % "1.0.2"
lazy val scalaParser = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.1"
lazy val dispatchV = "0.11.2" // change this to appropriate dispatch version
lazy val dispatch = "net.databinder.dispatch" %% "dispatch-core" % dispatchV


lazy val `tradehubbot` = (project in file(".")).
  enablePlugins(PlayScala).
//  enablePlugins(ScalaxbPlugin).
  settings(commonSettings: _*).
  settings(
    name := "nada",
    libraryDependencies ++= Seq(dispatch),
    libraryDependencies ++= {
      if (scalaVersion.value startsWith "2.11") Seq(scalaXml, scalaParser)
      else Seq()
    }).
  settings(scalaxbSettings: _*).
  settings(
    sourceGenerators in Compile += (scalaxb in Compile).taskValue,
    xsdSource in(Compile, scalaxb) := new File("app/xsd"),
    wsdlSource in(Compile, scalaxb) := new File("app/wsdl"),
    dispatchVersion in(Compile, scalaxb) := dispatchV,
    async in(Compile, scalaxb) := true,
    packageName in(Compile,scalaxb) := "WebService"
  )
