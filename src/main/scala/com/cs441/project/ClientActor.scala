package com.cs441.project

import akka.actor.Actor
import akka.event.Logging
import com.cs441.project.objects.Movie
import com.cs441.project.protocols.FileManagementProtocol.{Get, GetServer, Put, PutServer, RespondGet, RespondPut}

class ClientActor extends Actor {

  private val log = Logging(context.system, this)

  override def receive: Receive = {

    case Get(name, entry) =>
      entry ! GetServer(name, self)
    case Put(file, entry) =>
      entry ! PutServer(file, self)

    case RespondGet(query, value, location) =>
      value match {
        case Some(movie) => log.info(self.path.name + " received " + movie.asInstanceOf[Movie].getName + " from " + location + "." )
        case None => log.info(self.path.name + " failed to retrieve " + query + " ." )
      }
    case RespondPut(query, value, location) =>
      value match {
        case Some(b) => log.info(self.path.name + " successfully put " + query + " in " + location + "." )
        case None => log.info(self.path.name + " failed to put " + query + " ." )
      }
  }
}
