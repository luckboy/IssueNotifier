import sbt._
import sbt.Keys._
import AndroidKeys._

object Build extends Build
{
  lazy val main = Project(
      id = "IssueNotifier",
      base = file("."),
      settings = (
          Project.defaultSettings ++
          AndroidProject.androidSettings ++
          AndroidManifestGenerator.settings ++
          TypedResources.settings ++
          Seq(name := "IssueNotifier",
              version := "0.1.0",
              versionCode := 0,
              scalaVersion := "2.9.2",
              platformName in Android := "android-11",
              useProguard in Android := true,
              libraryDependencies += "com.github.rjeschke" % "txtmark" % "0.13",
              javaOptions ++= Seq("-source", "1.6"),
              javaOptions ++= Seq("-target", "1.6"))))
}
