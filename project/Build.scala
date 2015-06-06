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
              versionCode := 1,
              scalaVersion := "2.9.2",
              platformName in Android := "android-11",
              useProguard in Android := true,
              libraryDependencies += "com.github.rjeschke" % "txtmark" % "0.13",
              libraryDependencies += "org.jsoup" % "jsoup" % "1.8.2",
              javaOptions ++= Seq("-source", "1.6"),
              javaOptions ++= Seq("-target", "1.6"),
              commands ++= Seq(svgsToDrawables))))
  
  val svgsToDrawables = Command.command("svgs-to-drawables") {
    state =>
      for(p <- Seq("ldpi" -> 0.75, "mdpi" -> 1.0, "hdpi" -> 1.5, "xhdpi" -> 2.0, "xxhdpi" -> 3.0, "xxxhdpi" -> 4.0)) {
        val dir = file("src") / "main" / "res" / ("drawable-" + p._1)
        dir.mkdirs()
        for(svg <- ((file("src") / "main" / "svg") ** "*.svg").getPaths) {
          val png = dir / (file(svg).base + ".png")
          val xml = scala.xml.XML.loadFile(svg)
          val (wv, wu) = (xml \ "@width").toString.partition { _.isDigit } 
          val (hv, hu) = (xml \ "@height").toString.partition { _.isDigit }
          ("inkscape -z -w " + (wv.toDouble * p._2) + wu + " -h " + (hv.toDouble * p._2) + hu + " -e " + png + " " + svg).!
        }
      }
      state
  }
}
