package scalafiddle.router

import org.slf4j.LoggerFactory
import upickle.Js
import upickle.default._

import scalafiddle.shared._

object Librarian {

  case class LibraryVersion(
      version: String,
      scalaVersions: Seq[String],
      extraDeps: Seq[String],
      organization: Option[String],
      artifact: Option[String],
      doc: Option[String]
  )

  implicit val libraryVersionReader = upickle.default.Reader[LibraryVersion] {
    case Js.Obj(valueSeq @ _*) =>
      val values = valueSeq.toMap
      LibraryVersion(
        readJs[String](values("version")),
        readJs[Seq[String]](values("scalaVersions")),
        readJs[Seq[String]](values.getOrElse("extraDeps", Js.Arr())),
        values.get("organization").map(readJs[String]),
        values.get("artifact").map(readJs[String]),
        values.get("doc").map(readJs[String])
      )
  }

  case class LibraryDef(
      name: String,
      organization: String,
      artifact: String,
      doc: String,
      versions: Seq[LibraryVersion],
      compileTimeOnly: Boolean
  )

  case class LibraryGroup(
      group: String,
      libraries: Seq[LibraryDef]
  )

  val repoSJSRE = """([^ %]+) *%%% *([^ %]+) *% *([^ %]+)""".r
  val repoRE    = """([^ %]+) *%% *([^ %]+) *% *([^ %]+)""".r

  def loadLibraries(data: String): Map[String, Set[ExtLib]] = {
    val libGroups = read[Seq[LibraryGroup]](data)
    (for {
      group      <- libGroups
      lib        <- group.libraries
      versionDef <- lib.versions
    } yield {
      versionDef.scalaVersions.flatMap { scalaVersion =>
        val extraDeps = versionDef.extraDeps.map {
          case repoSJSRE(grp, artifact, version) =>
            scalaVersion -> ExtLib(grp, artifact, version, compileTimeOnly = false)
          case repoRE(grp, artifact, version) =>
            scalaVersion -> ExtLib(grp, artifact, version, compileTimeOnly = true)
        }
        Seq(
          scalaVersion -> ExtLib(
            versionDef.organization.getOrElse(lib.organization),
            versionDef.artifact.getOrElse(lib.artifact),
            versionDef.version,
            lib.compileTimeOnly
          )) ++ extraDeps
      }
    }).flatten.groupBy(_._1).map { case (version, libs) => version -> libs.map(_._2).toSet }
  }
}
