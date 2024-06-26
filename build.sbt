organization := "org.havelund"

name := "daut"

version := "0.2"

scalaVersion := "3.4.2"

// libraryDependencies += "com.github.tototoshi" %% "scala-csv" % "1.3.6"
libraryDependencies += "de.siegmar" %"fastcsv" %"1.0.1"

libraryDependencies += "org.json4s" %% "json4s-native" % "4.0.6"
libraryDependencies += "org.json4s" %% "json4s-ext" % "4.0.6"

// for generating JSON from case classes:

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core" % "0.14.1",
  "io.circe" %% "circe-generic" % "0.14.1",
  "io.circe" %% "circe-parser" % "0.14.1"
)

// ---

scalacOptions += "-explain"
scalacOptions += "-explain-cyclic"

enablePlugins(GenerateRunScript)

