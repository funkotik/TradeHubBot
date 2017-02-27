logLevel := Level.Warn

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("org.scalaxb" % "sbt-scalaxb" % "1.2.0")

resolvers += Resolver.sonatypeRepo("public")

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.5.4")
