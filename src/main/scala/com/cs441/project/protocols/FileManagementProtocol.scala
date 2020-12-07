package com.cs441.project.protocols

import akka.actor.ActorRef
import com.cs441.project.objects.Movie

object FileManagementProtocol {
  case class Get(name: String, entry: ActorRef)
  case class Put(file: Movie, entry: ActorRef)

  case class GetServer(name: String, replyTo: ActorRef)
  case class PutServer(file:Movie, replyTo: ActorRef)

  case class AddFile(file:Movie, replyTo:ActorRef)
  case class GetFile(name: String, replyTo: ActorRef)

  case class RespondGet(query:String, value: Option[Movie], location:Int)
  case class RespondPut(query: String, value: Option[Boolean], location:Int)
}
