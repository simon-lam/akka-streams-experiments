package io.simonlam.exp.stream

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Source}
import akka.stream.testkit.scaladsl.TestSink
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class FlowRateGaugeSpec extends AnyFlatSpec with Matchers with ScalaFutures with BeforeAndAfterAll with Eventually {

  implicit val actorSystem = ActorSystem("flow-rate-gauge-spec")

  override def afterAll(): Unit = actorSystem.terminate()

  "FlowRateGauge" should "have no effects on actual stream processing" in {
    val intList = (1 to 1000).toList
    val result = Source(intList)
      .via(Flow.fromGraph(new FlowRateGauge[Int]))
      .runFold(List.empty[Int])((agg, el) => agg ::: List(el)).futureValue
    result shouldBe intList
  }

  it should "allow flow rate readings from outside the stream" in {
    val (gaugeProbe, testProbe) = Source((1 to 100000).toList)
      .viaMat(Flow.fromGraph(new FlowRateGauge[Int]))(Keep.right)
      .toMat(TestSink.probe[Int])(Keep.both)
      .run()

    gaugeProbe.takeReading shouldBe 0
    testProbe.request(50000).expectNextN(50000)
    eventually {
      assert(gaugeProbe.takeReading > 0)
    }
    eventually(Timeout(2 seconds)){
      // Because we do not explicitly request for more; the rate should drop back down to zero
      gaugeProbe.takeReading shouldBe 0
    }
  }
}
