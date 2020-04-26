package io.simonlam.exp.stream

import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import akka.stream.stage.{GraphStageLogic, GraphStageWithMaterializedValue, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}

class ElementCounter[T] extends GraphStageWithMaterializedValue[FlowShape[T, T], ElementCounterProbe] {

  val in = Inlet[T]("ElementCounter.in")
  val out = Outlet[T]("ElementCounter.out")

  override def shape: FlowShape[T, T] = FlowShape(in, out)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, ElementCounterProbe) = {
    val elementCount = new AtomicLong(0L)
    val isStreamActive = new AtomicBoolean(true)

    val logic = new GraphStageLogic(shape) with InHandler with OutHandler {
      override def onPush(): Unit = push(out, {
        elementCount.incrementAndGet()
        grab(in)
      })

      override def onPull(): Unit = pull(in)

      override def onUpstreamFinish(): Unit = {
        isStreamActive.set(false)
        super.onUpstreamFinish()
      }

      override def onUpstreamFailure(ex: Throwable): Unit = {
        isStreamActive.set(false)
        super.onUpstreamFailure(ex)
      }

      override def onDownstreamFinish(cause: Throwable): Unit = {
        isStreamActive.set(false)
        super.onDownstreamFinish(cause)
      }

      setHandlers(in, out, this)
    }

    (logic, new ElementCounterProbe {
      override def count: Long = elementCount.get()

      override def isStreamFinished: Boolean = !isStreamActive.get()
    })
  }
}

trait ElementCounterProbe {
  def count: Long
  def isStreamFinished: Boolean
}
