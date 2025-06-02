ThisBuild / scalaVersion       := "2.13.14"
ThisBuild / version            := "0.4.0"
ThisBuild / organization       := "com.github.andurdur"

val chiselVersion = "3.6.1" // Using edu.berkeley.cs group ID
val scrimageVersion = "4.1.1"

lazy val root = (project in file("."))
  .settings(
    name := "Chroma_Subsampling_Image_Compressor",
    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "chisel3" % chiselVersion,
      "edu.berkeley.cs" %% "chiseltest" % "0.6.2" % "test"
    ),
    scalacOptions ++= Seq(
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit"
    ),
    addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % chiselVersion cross CrossVersion.full),

    Test / fork := true, // Important: run tests in a separate forked JVM
    Test / javaOptions ++= Seq(
      "-Xms1G",         // Initial Java heap size for tests (e.g., 1 Gigabyte)
      "-Xmx4G",         // Maximum Java heap size for tests (e.g., 4 Gigabytes)
      // Optionally, you could add a different garbage collector if memory issues persist with larger heaps:
      // "-XX:+UseG1GC"
    )

  )

libraryDependencies += "org.scalatestplus" %% "junit-4-13" % "3.2.15.0" % "test"

libraryDependencies += "com.sksamuel.scrimage" % "scrimage-core" % scrimageVersion
libraryDependencies += "com.sksamuel.scrimage" % "scrimage-filters" % scrimageVersion
libraryDependencies += "com.sksamuel.scrimage" %% "scrimage-io-extra" % scrimageVersion // Changed from scrimage-scala for better format support


