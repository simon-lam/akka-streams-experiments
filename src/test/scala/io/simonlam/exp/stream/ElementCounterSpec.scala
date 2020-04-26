package io.simonlam.exp.stream

import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Source}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers

class ElementCounterSpec extends AnyFlatSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  implicit val actorSystem = ActorSystem("element-counter-spec")

  override def afterAll(): Unit = actorSystem.terminate()

  "ElementCounter" should "have no effects on actual stream processing" in {
    val intList = (1 to 1000).toList
    val result = Source(intList)
      .via(Flow.fromGraph(new ElementCounter))
      .runFold(List.empty[Int])((agg, el) => agg ::: List(el)).futureValue
    result shouldBe intList
  }
}
