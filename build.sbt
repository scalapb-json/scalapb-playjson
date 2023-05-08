import scalapb.compiler.Version._
import sbtrelease.ReleaseStateTransformations._
import sbtcrossproject.CrossPlugin.autoImport.crossProject

val Scala212 = "2.12.17"
val playJsonVersion = settingKey[String]("")
val scalapbJsonCommonVersion = settingKey[String]("")

val tagName = Def.setting {
  s"v${if (releaseUseGlobalVersion.value) (ThisBuild / version).value else version.value}"
}

val tagOrHash = Def.setting {
  if (isSnapshot.value) sys.process.Process("git rev-parse HEAD").lineStream_!.head
  else tagName.value
}

val unusedWarnings = Def.setting(
  Seq("-Ywarn-unused:imports")
)

lazy val disableScala3 = Def.settings(
  libraryDependencies := {
    if (scalaBinaryVersion.value == "3") {
      Nil
    } else {
      libraryDependencies.value
    }
  },
  Seq(Compile, Test).map { x =>
    (x / sources) := {
      if (scalaBinaryVersion.value == "3") {
        Nil
      } else {
        (x / sources).value
      }
    }
  },
  Test / test := {
    if (scalaBinaryVersion.value == "3") {
      ()
    } else {
      (Test / test).value
    }
  },
  publish / skip := (scalaBinaryVersion.value == "3"),
)

lazy val macros = project
  .in(file("macros"))
  .settings(
    commonSettings,
    name := UpdateReadme.scalapbPlayJsonMacrosName,
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-json" % playJsonVersion.value, // don't use %%%
      "io.github.scalapb-json" %%% "scalapb-json-macros" % scalapbJsonCommonVersion.value,
    ),
  )
  .dependsOn(
    scalapbPlayJsonJVM,
  )

lazy val tests = crossProject(JVMPlatform, JSPlatform)
  .in(file("tests"))
  .settings(
    commonSettings,
    noPublish,
  )
  .configure(_ dependsOn macros)
  .platformsSettings(JSPlatform)(
    disableScala3,
  )
  .dependsOn(
    scalapbPlayJson % "test->test",
  )

val scalapbPlayJson = crossProject(JVMPlatform, JSPlatform)
  .in(file("core"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    commonSettings,
    name := UpdateReadme.scalapbPlayJsonName,
    libraryDependencies += "com.typesafe.play" %%% "play-json" % playJsonVersion.value,
    (Compile / packageSrc / mappings) ++= (Compile / managedSources).value.map { f =>
      // https://github.com/sbt/sbt-buildinfo/blob/v0.7.0/src/main/scala/sbtbuildinfo/BuildInfoPlugin.scala#L58
      val buildInfoDir = "sbt-buildinfo"
      val path = if (f.getAbsolutePath.contains(buildInfoDir)) {
        (file(buildInfoPackage.value) / f
          .relativeTo((Compile / sourceManaged).value / buildInfoDir)
          .get
          .getPath).getPath
      } else {
        f.relativeTo((Compile / sourceManaged).value).get.getPath
      }
      (f, path)
    },
    buildInfoPackage := "scalapb_playjson",
    buildInfoObject := "ScalapbPlayJsonBuildInfo",
    buildInfoKeys := Seq[BuildInfoKey](
      "scalapbVersion" -> scalapbVersion,
      playJsonVersion,
      scalapbJsonCommonVersion,
      scalaVersion,
      version
    )
  )
  .jvmSettings(
    Test / PB.targets ++= Seq[protocbridge.Target](
      PB.gens.java -> (Test / sourceManaged).value,
      scalapb.gen(javaConversions = true) -> (Test / sourceManaged).value
    ),
    libraryDependencies ++= Seq(
      "com.google.protobuf" % "protobuf-java-util" % "3.23.0" % "test",
      "com.google.protobuf" % "protobuf-java" % "3.23.0" % "protobuf"
    )
  )
  .jsSettings(
    buildInfoKeys ++= Seq[BuildInfoKey](
      "scalajsVersion" -> scalaJSVersion
    ),
    scalacOptions += {
      val a = (LocalRootProject / baseDirectory).value.toURI.toString
      val g = "https://raw.githubusercontent.com/scalapb-json/scalapb-playjson/" + tagOrHash.value
      if (scalaBinaryVersion.value == "3") {
        "-scalajs-mapSourceURI:$a->$g/"
      } else {
        "-P:scalajs:mapSourceURI:$a->$g/"
      }
    },
  )
  .platformsSettings(JSPlatform)(
    Test / PB.targets ++= Seq[protocbridge.Target](
      scalapb.gen(javaConversions = false) -> (Test / sourceManaged).value
    )
  )

commonSettings

val noPublish = Seq(
  PgpKeys.publishLocalSigned := {},
  PgpKeys.publishSigned := {},
  publishLocal := {},
  publish := {},
  Compile / publishArtifact := false
)

noPublish

lazy val commonSettings = Def.settings(
  scalapropsCoreSettings,
  (Compile / unmanagedResources) += (LocalRootProject / baseDirectory).value / "LICENSE.txt",
  scalaVersion := Scala212,
  crossScalaVersions := Seq(Scala212, "2.13.10", "3.3.0-RC5"),
  scalacOptions ++= unusedWarnings.value,
  Seq(Compile, Test).flatMap(c => (c / console / scalacOptions) --= unusedWarnings.value),
  scalacOptions ++= Seq("-feature", "-deprecation", "-language:existentials"),
  description := "Json/Protobuf convertors for ScalaPB",
  licenses += ("MIT", url("https://opensource.org/licenses/MIT")),
  organization := "io.github.scalapb-json",
  Project.inConfig(Test)(sbtprotoc.ProtocPlugin.protobufConfigSettings),
  Compile / PB.targets := Nil,
  (Test / PB.protoSources) := Seq(baseDirectory.value.getParentFile / "shared/src/test/protobuf"),
  scalapbJsonCommonVersion := "0.8.9",
  playJsonVersion := {
    scalaBinaryVersion.value match {
      case "3" =>
        "2.10.0-RC8"
      case _ =>
        "2.9.4"
    }
  },
  libraryDependencies ++= Seq(
    "com.github.scalaprops" %%% "scalaprops" % "0.9.1" % "test",
    "com.github.scalaprops" %%% "scalaprops-shapeless" % "0.5.1" % "test",
    "io.github.scalapb-json" %%% "scalapb-json-common" % scalapbJsonCommonVersion.value,
    "com.thesamet.scalapb" %%% "scalapb-runtime" % scalapbVersion % "protobuf,test",
    "org.scalatest" %%% "scalatest" % "3.2.15" % "test"
  ),
  Global / pomExtra := {
    <url>https://github.com/scalapb-json/scalapb-playjson</url>
      <scm>
        <connection>scm:git:github.com/scalapb-json/scalapb-playjson.git</connection>
        <developerConnection>scm:git:git@github.com:scalapb-json/scalapb-playjson.git</developerConnection>
        <url>github.com/scalapb-json/scalapb-playjson.git</url>
        <tag>{tagOrHash.value}</tag>
      </scm>
      <developers>
        <developer>
          <id>xuwei-k</id>
          <name>Kenji Yoshida</name>
          <url>https://github.com/xuwei-k</url>
        </developer>
      </developers>
  },
  publishTo := sonatypePublishToBundle.value,
  (Compile / doc / scalacOptions) ++= {
    val t = tagOrHash.value
    Seq(
      "-sourcepath",
      (LocalRootProject / baseDirectory).value.getAbsolutePath,
      "-doc-source-url",
      s"https://github.com/scalapb-json/scalapb-playjson/tree/${t}€{FILE_PATH}.scala"
    )
  },
  ReleasePlugin.extraReleaseCommands,
  commands += Command.command("updateReadme")(UpdateReadme.updateReadmeTask),
  releaseCrossBuild := true,
  releaseTagName := tagName.value,
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    UpdateReadme.updateReadmeProcess,
    tagRelease,
    ReleaseStep(
      action = { state =>
        val extracted = Project extract state
        extracted
          .runAggregated(extracted.get(thisProjectRef) / (Global / PgpKeys.publishSigned), state)
      },
      enableCrossBuild = true
    ),
    releaseStepCommand("sonatypeBundleRelease"),
    setNextVersion,
    commitNextVersion,
    UpdateReadme.updateReadmeProcess,
    pushChanges
  )
)

val scalapbPlayJsonJVM = scalapbPlayJson.jvm
val scalapbPlayJsonJS = scalapbPlayJson.js
