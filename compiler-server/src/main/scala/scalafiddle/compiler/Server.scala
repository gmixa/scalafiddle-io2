package scalafiddle.compiler

import akka.actor.{ActorSystem, Props}
import akka.util.Timeout
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

object Server extends App {
  implicit val system  = ActorSystem()
  implicit val timeout = Timeout(30.seconds)
  implicit val ec      = system.dispatcher
  val log              = LoggerFactory.getLogger(getClass)

  log.info(s"Scala Fiddle compiler ${Config.version}")

  val manager = system.actorOf(Props[Manager], "Manager")
}
