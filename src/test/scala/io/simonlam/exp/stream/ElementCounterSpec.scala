package io.simonlam.exp.stream

import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Source}
import akka.stream.testkit.scaladsl.TestSink
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

  it should "report seen elements from outside the stream" in {
    val (counterProbe, testProbe) = Source((1 to 5).toList)
      .viaMat(Flow.fromGraph(new ElementCounter))(Keep.right)
      .toMat(TestSink.probe[Int])(Keep.both)
      .run()

    counterProbe.count shouldBe 0L

    testProbe.request(1).expectNext(1)
    counterProbe.count shouldBe 1L

    testProbe.request(3).expectNext(2, 3, 4)
    counterProbe.count shouldBe 4L
    counterProbe.isStreamFinished shouldBe false

    testProbe.request(3).expectNext(5).expectComplete()
    counterProbe.count shouldBe 5L
    counterProbe.isStreamFinished shouldBe true
  }
}
