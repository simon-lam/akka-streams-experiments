package io.simonlam.exp.stream

import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

import akka.stream.stage._
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}

class FlowRateGauge[T] extends GraphStageWithMaterializedValue[FlowShape[T, T], FlowRateGaugeProbe] {

  val in = Inlet[T]("FlowRateGauge.in")
  val out = Outlet[T]("FlowRateGauge.out")

  override def shape: FlowShape[T, T] = FlowShape(in, out)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, FlowRateGaugeProbe) = {
    val logic = new FlowRateGaugeGraphStageLogic[T](shape)

    (logic, new FlowRateGaugeProbe {
      override def delayedReading: BigDecimal = logic.delayedReading.asElementsPerSecond

      override def takeReading(duration: FiniteDuration)
                              (implicit ec: ExecutionContext): Future[BigDecimal] = {
        logic.takeReading(duration).map(_.asElementsPerSecond)
      }
    })
  }
}

class FlowRateGaugeGraphStageLogic[T](shape: FlowShape[T, T], interval: FiniteDuration = 1 seconds)
  extends TimerGraphStageLogic(shape) with InHandler with OutHandler {

  private final val DelayedReadingTimer = "delayed-reading"

  private val in = shape.in
  private val out = shape.out

  private val elementsSeen = new AtomicLong(0L)

  override def onPush(): Unit = push(out, {
    initDelayedReadingTimer()
    elementsSeen.incrementAndGet()
    grab(in)
  })

  override def onPull(): Unit = pull(in)

  setHandlers(in, out, this)

  private val readings = mutable.Map.empty[String, FlowRateGaugeReading]
  private val readingsInProgress = mutable.Map.empty[String, Promise[FlowRateGaugeReading]]

  def delayedReading = {
    val currentElementCount = elementsSeen.get()
    readings.getOrElseUpdate(DelayedReadingTimer,
      FlowRateGaugeReading(currentElementCount, currentElementCount, interval))
  }

  def takeReading(duration: FiniteDuration): Future[FlowRateGaugeReading] = {
    val readingKey = UUID.randomUUID().toString
    val currentElementCount = elementsSeen.get()
    readings.update(readingKey, FlowRateGaugeReading(currentElementCount, currentElementCount, duration))
    val promise = Promise[FlowRateGaugeReading]()
    readingsInProgress.update(readingKey, promise)
    scheduleOnce(readingKey, duration)
    promise.future
  }

  private def initDelayedReadingTimer(): Unit = {
    if (!isTimerActive(DelayedReadingTimer)) {
      scheduleAtFixedRate(DelayedReadingTimer, 0 seconds, interval)
    }
  }

  override def onTimer(timerKey: Any): Unit = {
    timerKey match {
      case r: String if r == DelayedReadingTimer =>
        val currentElementCount = elementsSeen.get()
        val sample = delayedReading
        readings.update(r, sample.update(currentElementCount))

      case r: String =>
        val currentElementCount = elementsSeen.get()
        val reading = readings.get(r).get // TODO
        val promise = readingsInProgress.get(r).get // TODO
        promise.success(reading.update(currentElementCount))
        readingsInProgress.remove(r)
        readings.remove(r)
    }
  }
}

case class FlowRateGaugeReading(startingElementCount: Long,
                                endingElementCount: Long,
                                duration: FiniteDuration,
                                timestamp: ZonedDateTime = ZonedDateTime.now()) {
  def update(recordedElementCount: Long) = this.copy(
    startingElementCount = endingElementCount,
    endingElementCount = recordedElementCount,
    timestamp = ZonedDateTime.now())

  def asElementsPerSecond: BigDecimal = {
    ((endingElementCount - startingElementCount) / duration.toMillis) * 1000
  }
}

trait FlowRateGaugeProbe {

  def delayedReading: BigDecimal

  def takeReading(duration: FiniteDuration)(implicit ec: ExecutionContext): Future[BigDecimal]
}
