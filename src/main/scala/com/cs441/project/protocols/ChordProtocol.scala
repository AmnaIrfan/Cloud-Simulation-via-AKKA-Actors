package com.cs441.project.protocols

import akka.actor.ActorRef
import com.cs441.project.objects.ActorRefWrapper

object ChordProtocol {
  //todo: check
  //todo: should we use paths or ActorRefs for successors and predecessors
  case class Create(queue:Int, replyTo: ActorRef)
  case class CreateRespond(id: Int, queue: Int, replyTo: ActorRef)

  case class Join(existingNode: ActorRef, queue:Int, replyTo: ActorRef)
  case class JoinRespond(id: Int, queue:Int, replyTo: ActorRef)
  //todo: arrived here

  case class GetPredecessor(replyTo: ActorRef)
  case class GetPredecessorRespond(predecessor: ActorRefWrapper)

  case class FindSuccessor(id: ActorRef)
  case class FindSuccessorID(id: Int, initiator: ActorRefWrapper)

  case class Stabilize()
  case class StabilizeRespond()

  case class Notify(n1: ActorRef)

  case class FixFingers()
  case class FixFingersRespond()

  case class Ping()
  case class CheckPredecessor()

}
