package scalafiddle.shared

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should
import upickle.default._
class CompilerMessageSpec extends AnyFlatSpec with should.Matchers {
  "writing a compiler message" should "create CompilerReady" in {
    write[CompilerMessage](CompilerReady) should equal("{\"$type\":\"scalafiddle.shared.CompilerReady\"}")
  }
  it should "create Ping" in {
    write[CompilerMessage](Ping) should equal("{\"$type\":\"scalafiddle.shared.Ping\"}")
  }
  it should "create Pong" in {
    write[CompilerMessage](Pong) should equal("{\"$type\":\"scalafiddle.shared.Pong\"}")
  }
  it should "create UpdateLibraries(Nil)" in {
    write[CompilerMessage](UpdateLibraries(Nil)) should equal(
      "{\"$type\":\"scalafiddle.shared.UpdateLibraries\",\"libs\":[]}"
    )
  }
}
