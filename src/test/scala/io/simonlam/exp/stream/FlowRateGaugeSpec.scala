package io.simonlam.exp.stream

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class FlowRateGaugeSpec extends AnyFlatSpec with Matchers with ScalaFutures with BeforeAndAfterAll with Eventually {

  private implicit val actorSystem = ActorSystem("flow-rate-gauge-spec")
  private implicit val ec = actorSystem.dispatcher

  override def afterAll(): Unit = actorSystem.terminate()

  "FlowRateGauge" should "have no effects on actual stream processing" in {
    val intList = (1 to 1000).toList
    val result = Source(intList)
      .via(Flow.fromGraph(new FlowRateGauge[Int]))
      .runFold(List.empty[Int])((agg, el) => agg ::: List(el)).futureValue
    result shouldBe intList
  }

  it should "allow flow rate readings from outside the stream" in {
    val gaugeProbe = Source(1 to 1000000000)
      .viaMat(Flow.fromGraph(new FlowRateGauge[Int]))(Keep.right)
      .to(Sink.ignore)
//      .toMat(TestSink.probe[Int])(Keep.both)
      .run()

    assert(gaugeProbe.takeReading(100 millis).futureValue > 0)
  }
}
