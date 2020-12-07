package com.cs441.project.objects

import akka.actor.ActorRef

class ActorRefWrapper(actorRef: ActorRef) {
  def getActorRef(): ActorRef = {
    return actorRef
  }
}