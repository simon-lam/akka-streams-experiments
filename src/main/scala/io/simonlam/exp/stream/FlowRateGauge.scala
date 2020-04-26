package io.simonlam.exp.stream

import java.time.ZonedDateTime
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import akka.stream.stage._
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}

import scala.collection.mutable
import scala.concurrent.duration._

class FlowRateGauge[T] extends GraphStageWithMaterializedValue[FlowShape[T, T], FlowRateGaugeProbe] {

  val in = Inlet[T]("FlowRateGauge.in")
  val out = Outlet[T]("FlowRateGauge.out")

  override def shape: FlowShape[T, T] = FlowShape(in, out)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, FlowRateGaugeProbe) = {
    val logic = new FlowRateGaugeGraphStageLogic[T](shape)

    (logic, new FlowRateGaugeProbe {
      override def takeReading: BigDecimal = logic.flowRate.asElementsPerSecond
    })
  }
}

class FlowRateGaugeGraphStageLogic[T](shape: FlowShape[T, T], interval: FiniteDuration = 1 seconds)
  extends TimerGraphStageLogic(shape) with InHandler with OutHandler {

  private final val DefaultSamplingTimer = "default-sample"

  private val in = shape.in
  private val out = shape.out

  private val elementsProcessed = new AtomicLong(0L)

  override def onPush(): Unit = push(out, {
    initDefaultSamplingTimer()
    elementsProcessed.incrementAndGet()
    grab(in)
  })

  override def onPull(): Unit = pull(in)

  private val samples = mutable.Map.empty[String, FlowRateGaugeSample]

  private def initDefaultSamplingTimer(): Unit = {
    if (!isTimerActive(DefaultSamplingTimer)) {
      scheduleAtFixedRate(DefaultSamplingTimer, 0 seconds, interval)
    }
  }

  override def onTimer(timerKey: Any): Unit = {
    timerKey match {
      case sampleKey: String =>
        val currentElementCount = elementsProcessed.get()
        val sample = flowRate
        samples.update(sampleKey, sample.update(currentElementCount))
    }
  }

  def flowRate = {
    val currentElementCount = elementsProcessed.get()
    samples.getOrElseUpdate(DefaultSamplingTimer,
      FlowRateGaugeSample(currentElementCount, currentElementCount, interval))
  }

  setHandlers(in, out, this)
}

case class FlowRateGaugeSample(startingElementCount: Long,
                               endingElementCount: Long,
                               duration: Duration,
                               timestamp: ZonedDateTime = ZonedDateTime.now()) {
  def update(recordedElementCount: Long) = this.copy(
    startingElementCount = endingElementCount,
    endingElementCount = recordedElementCount,
    timestamp = ZonedDateTime.now())

  def asElementsPerSecond: BigDecimal = {
    (endingElementCount - startingElementCount) / duration.toSeconds
  }
}

trait FlowRateGaugeProbe {
  // Elements per second
  def takeReading: BigDecimal
}
