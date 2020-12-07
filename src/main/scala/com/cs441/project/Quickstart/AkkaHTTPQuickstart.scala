package com.cs441.project
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import scala.io.StdIn

object AkkaChordServer {
  def main(args: Array[String]) {

    implicit val system = ActorSystem("chord-simulator")
    implicit val executionContext = system.dispatcher

    val respSuccess = <state><response>Success</response></state>

    val route =
      concat(
        path("start-simulation") {
          get {
              complete(HttpEntity(ContentTypes.`text/xml(UTF-8)`, respSuccess.toString))
          }
        },
        path("stop-simulation") {
          get {
            complete(HttpEntity(ContentTypes.`text/xml(UTF-8)`, respSuccess.toString))
          }
        },
        path("simulation-state") {
          get {
            complete(HttpEntity(ContentTypes.`text/xml(UTF-8)`, respSuccess.toString))
          }
        },
        path("movie") {
          get {
            parameters('name.as[String]) { (name) =>
              complete(HttpEntity(ContentTypes.`text/xml(UTF-8)`, respSuccess.toString))
            }
          }
        },
        path("movie") {
          post {
            parameters('name.as[String]) { (name) =>
              complete(HttpEntity(ContentTypes.`text/xml(UTF-8)`, respSuccess.toString))
            }
          }
        })

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Akka Chord Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}