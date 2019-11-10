import sbt._
import sbt.Keys._

object FMPP {
  def preprocessorSettings = inConfig(Compile)(Seq(sourceGenerators += fmpp.taskValue, fmpp := fmppTask.value)) ++ Seq(
    libraryDependencies ++= Seq(
      ("net.sourceforge.fmpp" % "fmpp" % "0.9.15" % FmppConfig.name).intransitive,
      "org.freemarker" % "freemarker" % "2.3.23" % FmppConfig.name,
      "oro" % "oro" % "2.0.8" % FmppConfig.name,
      "org.beanshell" % "bsh" % "2.0b5" % FmppConfig.name,
      "xml-resolver" % "xml-resolver" % "1.2" % FmppConfig.name
    ),
    ivyConfigurations += FmppConfig,
    fullClasspath in FmppConfig := update.map { _ select configurationFilter(FmppConfig.name) map Attributed.blank }.value,
    mappings in (Compile, packageSrc) ++= {
      val fmppSrc = (sourceDirectory in Compile).value / "scala"
      val inFiles = fmppSrc ** "*.fm"
      ((managedSources in Compile).value.pair(Path.relativeTo((sourceManaged in Compile).value) | Path.flat)) ++ // Add generated sources to sources JAR
      (inFiles pair (Path.relativeTo(fmppSrc) | Path.flat)) // Add *.fm files to sources JAR
    }
  )
  /* FMPP Task */
  val fmpp = TaskKey[Seq[File]]("fmpp")
  val FmppConfig = config("fmpp").hide
  def fmppTask = Def.task {
    val s = streams.value
    val output = sourceManaged.value
    val fmppSrc = sourceDirectory.value / "scala"
    val inFiles = (fmppSrc ** "*.fm").get.toSet
    val cachedFun = FileFunction.cached(s.cacheDirectory / "fmpp", inStyle = FilesInfo.exists){ (in: Set[File]) =>
      IO.delete((output ** "*.scala").get)
      val args = "--expert" :: "-q" :: "-S" :: fmppSrc.getPath :: "-O" :: output.getPath ::
      "--replace-extensions=fm, scala" :: "-M" :: "execute(**/*.fm), ignore(**/*)" :: Nil

      val errors = (runner in fmpp).value.run("fmpp.tools.CommandLine", (fullClasspath in FmppConfig).value.files, args, s.log)

      errors foreach sys.error

      (output ** "*.scala").get.toSet
    }
    cachedFun(inFiles).toSeq
  }
}
