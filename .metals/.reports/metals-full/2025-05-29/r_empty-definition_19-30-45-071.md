error id: file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/build.sbt:`<none>`.
file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/build.sbt
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 261
uri: file://wsl.localhost/Ubuntu/home/anngvo/CSE-228A/Chroma-Subsampling-Image-Compressor/build.sbt
text:
```scala
ThisBuild / scalaVersion     := "2.13.14"
ThisBuild / version          := "0.4.0"
ThisBuild / organization     := "com.github.andurdur"

val chiselVersion = "3.6.1"
val scrimageVersion = "4.1.1"

lazy val root = (project in file("."))
  .settings(
    name := "@@Chroma_Subsampling_Image_Compressor",
    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "chisel3" % chiselVersion,
      "edu.berkeley.cs" %% "chiseltest" % "0.6.2" % "test"
    ),
    scalacOptions ++= Seq(
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit",
      "-P:chiselplugin:genBundleElements",
    ),
    addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % chiselVersion cross CrossVersion.full),
  )

libraryDependencies += "org.scalatestplus" %% "junit-4-13" % "3.2.15.0" % "test"

libraryDependencies += "com.sksamuel.scrimage" % "scrimage-core" % scrimageVersion
libraryDependencies += "com.sksamuel.scrimage" % "scrimage-filters" % scrimageVersion
libraryDependencies += "com.sksamuel.scrimage" %% "scrimage-scala" % scrimageVersion

```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.