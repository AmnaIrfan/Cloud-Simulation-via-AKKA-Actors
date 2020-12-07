package com.cs441.project.protocols

import akka.actor.ActorRef

object SimulationProtocol {
  case class Start()

  case class SpawnServer(queue: Int)
  case class SpawnServerRespond(queue: Int)
  //todo: arrived here

  case class PrintFingerTable()

  case class RunPeriodically()

  case class InitiateNodeFailure()
  case class NodeFailure(nodeId: Int)
  case class DumpSystemState()

}
