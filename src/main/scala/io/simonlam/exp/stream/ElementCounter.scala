package io.simonlam.exp.stream

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{GraphStage, GraphStageLogic, GraphStageWithMaterializedValue, InHandler, OutHandler}

class ElementCounter[T] extends GraphStage[FlowShape[T, T]] {

  val in = Inlet[T]("ElementCounter.in")
  val out = Outlet[T]("ElementCounter.out")

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      override def onPush(): Unit = push(out, grab(in))

      override def onPull(): Unit = pull(in)

      setHandlers(in, out, this)
    }

  override def shape: FlowShape[T, T] = FlowShape(in, out)
}


