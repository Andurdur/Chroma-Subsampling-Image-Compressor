ThisBuild / scalaVersion := "2.13.14"
ThisBuild / version      := "0.4.0"
ThisBuild / organization := "com.github.andurdur"

// ==== Performance and Resource Settings ====
ThisBuild / parallelExecution := true // Enables parallel execution of tasks
ThisBuild / useCoursier     := true   // Ensures Coursier is used for dependency resolution (default in newer sbt, but good to be explicit)

// JVM options for sbt itself (applied when sbt starts)
// For more persistent JVM options, consider creating a .jvmopts file in the project root
// e.g., with contents like:
//   -Xms1G
//   -Xmx4G
//   -XX:+UseG1GC
//   -XX:MaxMetaspaceSize=1G
// These can also be set for forked processes like 'run' or 'test' if needed:
// javaOptions ++= Seq("-Xms1G", "-Xmx4G") // Example for forked processes

val chiselVersion   = "3.6.1"
val scrimageVersion = "4.1.1"
val chiselTestVersion = "0.6.2" // It's good practice to define all versions as vals
val scalatestPlusJunitVersion = "3.2.15.0"

lazy val root = (project in file("."))
  .settings(
    name := "Chroma_Subsampling_Image_Compressor",
    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "chisel3" % chiselVersion,
      "edu.berkeley.cs" %% "chiseltest" % chiselTestVersion % Test, // Use 'Test' configuration
      "org.scalatestplus" %% "junit-4-13" % scalatestPlusJunitVersion % Test, // Use 'Test' configuration
      "com.sksamuel.scrimage" % "scrimage-core" % scrimageVersion,
      "com.sksamuel.scrimage" % "scrimage-filters" % scrimageVersion,
      "com.sksamuel.scrimage" %% "scrimage-scala" % scrimageVersion
    ),
    scalacOptions ++= Seq(
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit",
      "-P:chiselplugin:genBundleElements"
      // Consider adding optimization flags if appropriate for your project, e.g., "-O"
      // However, be mindful that aggressive optimizations can sometimes increase compile times.
    ),
    addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % chiselVersion cross CrossVersion.full),

    // JVM options for forked processes (e.g., run, test)
    // Adjust these values based on your project's needs and available system memory
    Test / fork := true, // Fork a new JVM for tests, allowing separate memory settings
    Test / javaOptions ++= Seq("-Xms512M", "-Xmx2G", "-XX:MaxMetaspaceSize=512m"),
    run / fork := true, // Fork a new JVM for running the application
    run / javaOptions ++= Seq("-Xms512M", "-Xmx2G", "-XX:MaxMetaspaceSize=512m"),

    // Optional: Configure garbage collector for potentially better performance
    // These are examples, and the best GC depends on the specific workload
    // javaOptions ++= Seq("-XX:+UseG1GC") // For sbt itself, place in .jvmopts
    // Test / javaOptions ++= Seq("-XX:+UseG1GC") // For forked test execution

    // Optional: Improve incremental compilation
    // zincOptions in ThisBuild := zincOptions.value.withAnalysisStoreFile(baseDirectory.value / "target" / "inc_compile_analysis.zip"),
    // This can sometimes help, but also might have overhead. Test if it benefits your project.

    // Optional: Cache resolution results for offline work or faster repeat builds
    // updateOptions := updateOptions.value.withCachedResolution(true) // Default is usually true
  )

// Note: The lines below were outside the .settings block in your original file.
// It's generally better to group all settings, especially libraryDependencies, within the .settings block
// or define them at the ThisBuild scope if they apply to all subprojects.
// I've moved them into the `libraryDependencies` sequence within `root.settings`.
// libraryDependencies += "org.scalatestplus" %% "junit-4-13" % "3.2.15.0" % "test"
// libraryDependencies += "com.sksamuel.scrimage" % "scrimage-core" % scrimageVersion
// libraryDependencies += "com.sksamuel.scrimage" % "scrimage-filters" % scrimageVersion
// libraryDependencies += "com.sksamuel.scrimage" %% "scrimage-scala" % scrimageVersion
