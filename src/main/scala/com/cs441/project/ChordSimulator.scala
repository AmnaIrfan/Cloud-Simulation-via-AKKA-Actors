package com.cs441.project

import java.io.{BufferedWriter, File, FileWriter}
import java.util.concurrent.TimeUnit

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.{Actor, ActorRef, ActorSystem, Props, Timers}
import akka.event.Logging
import com.cs441.project.protocols.SimulationProtocol.{DumpSystemState, InitiateNodeFailure, NodeFailure, PrintFingerTable, RunPeriodically, SpawnServer, SpawnServerRespond, Start}
import com.cs441.project.objects.Movie
import com.cs441.project.protocols.ChordProtocol.{CheckPredecessor, Create, CreateRespond, FixFingers, Join, JoinRespond, Notify, Stabilize}
import com.cs441.project.protocols.FileManagementProtocol.{Get, Put}

import scala.jdk.javaapi.CollectionConverters.asScala
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, ExecutionContext, Future}
import akka.pattern.ask

import scala.concurrent.duration._
import akka.util.Timeout
import com.cs441.project.ChordSimulator.settings
import com.cs441.project.utils.GenerateIP.generateIP
import com.cs441.project.utils.ConsistentHashing.getBucket
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._

import scala.io.StdIn

object ChordSimulator extends App {

  // Initialize Logger
  val LOG: Logger = LoggerFactory.getLogger(getClass)
  // Loading Config
  val settings: Config = ConfigFactory.load()

  // Import Simulation Input
  val num_users: Int = settings.getInt("Simulator.num_users")
  val min_requests_minute: Int = settings.getInt("Simulator.min_requests_minute")
  val max_requests_minute: Int = settings.getInt("Simulator.max_requests_minute")
  val sim_duration: Int = settings.getInt("Simulator.sim_duration")
  val time_marks: List[Int] = asScala(settings.getIntList("Simulator.time_marks")).toList.map(_.toInt)

  // Import Chord settings
  private val m: Int = settings.getInt("Simulator.m")
  private val ringSize: Int = settings.getInt("Simulator.ring_size")

  implicit val system: ActorSystem = ActorSystem("ChordSimulator")
  implicit val executionContext: ExecutionContext = system.dispatcher

  val chordSimulatorMain = system.actorOf(Props(classOf[ChordSimulatorMain], m, ringSize), "ChordSimulatorMain")
  //val chordSimulatorMain = system.actorOf(Props[ChordSimulatorMain], "ChordSimulatorMain")

  startRESTInterface()

  def startSimulator(): Unit = {
    chordSimulatorMain ! Start()
  }

  /*
  def dumpSystemStateToRest(ring: Map[Int, ActorRef]): Unit = {

    val bw = new StringBuilder

    bw.append("<state>\n")
    ring.values.foreach(a => {
      bw.append("<actor>\n")
      bw.append("<id>"+a.path.name+"</id>\n")

      bw.append("<fingers>\n")
      val printFuture = a ? PrintFingerTable()
      val printed: List[String] = Await.result(printFuture, timeout.duration).asInstanceOf[List[String]]

      printed.foreach(fline => {
        bw.append("<entry>\n")
        bw.append(fline)
        bw.append("</entry>\n")
      })

      bw.append("</fingers>\n")

      bw.append("</actor>\n")
    })
    bw.append("</state>\n")
  }
   */

  def startRESTInterface(): Unit = {

    implicit val timeout: Timeout = 5.seconds
    val respSuccess = <state><response>Success</response></state>
    val respDump = <state><response>Data saved to file dump.xml</response></state>

    val route =
      concat(
        path("start-simulation") {
          get {
            startSimulator()
            complete(HttpEntity(ContentTypes.`text/xml(UTF-8)`, respSuccess.toString))
          }
        },
        path("stop-simulation") {
          get {
            system.terminate()
            System.exit(0);
            complete(HttpEntity(ContentTypes.`text/xml(UTF-8)`, respSuccess.toString))
          }
        },
        path("simulation-state") {
          get {
            chordSimulatorMain ! DumpSystemState()
            complete(HttpEntity(ContentTypes.`text/xml(UTF-8)`, respDump.toString))
          }
        },
        path("movie") {
          get {
            parameters('name.as[String],'initClientId.as[String], 'initServcrtId.as[String]) { (name, clientId, serverId) =>

              val client = Await.result(system.actorSelection("../"+clientId).resolveOne(), timeout.duration)
              val server = Await.result(system.actorSelection("../"+serverId).resolveOne(), timeout.duration)

              client ! Get(name, server)

              complete(HttpEntity(ContentTypes.`text/xml(UTF-8)`, respSuccess.toString))
            }
          }
        },
        path("movie") {
          post {
            parameters('name.as[String],'initClientId.as[String], 'initServcrtId.as[String]) { (name, clientId, serverId) =>

              val client = Await.result(system.actorSelection("../"+clientId).resolveOne(), timeout.duration)
              val server = Await.result(system.actorSelection("../"+serverId).resolveOne(), timeout.duration)

              val movie1 = new Movie("sample1.mov")
              client ! Put(movie1, server)

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

class ChordSimulatorMain(m: Int, ringSize: Int) extends Actor {

  val random = new scala.util.Random
  implicit val timeout: Timeout = 5.seconds

  private val log = Logging(context.system, this)

  val ring: ArrayBuffer[Int] = new ArrayBuffer()

  val dumpOutput: String = settings.getString("Simulator.dumpOutput")

  var server11: ActorRef = null;
  var server22: ActorRef = null;
  var server33: ActorRef = null
  var server44: ActorRef = null

  override def receive: Receive = active(Map[Int, ActorRef]())

  private def active(ring: Map[Int, ActorRef]): Receive = {
    case Start() =>

      val ip = generateIP()
      val bucket = getBucket(ip)
      val server1 = context.system.actorOf(Props(classOf[ServerActor], bucket, m, ringSize), bucket.toString)

      //server1 ! Create(8, self)
      server1 ! Create(4, self)

      server11 = server1

      //context.system.scheduler.scheduleAtFixedRate(Duration(5, TimeUnit.SECONDS), Duration(5, TimeUnit.SECONDS), self, RunPeriodically())(context.system.dispatcher)
      context.system.scheduler.scheduleOnce(Duration(5, TimeUnit.SECONDS), self, RunPeriodically())(context.system.dispatcher)

      /*
    case RunPeriodically2() =>
      context.system.scheduler.scheduleOnce(30.seconds, self, DumpSystemState())(context.system.dispatcher)
      ring.values.foreach(a => {
        log.info("Run Periodically for actor "+a.path.name)
        context.system.scheduler.scheduleWithFixedDelay(Duration.Zero, 2000.milliseconds, a, Stabilize())(context.system.dispatcher)
        context.system.scheduler.scheduleWithFixedDelay(Duration.Zero, 2000.milliseconds, a, FixFingers())(context.system.dispatcher)
        //context.system.scheduler.scheduleWithFixedDelay(2000.milliseconds, 2000.milliseconds, a, CheckPredecessor())(context.system.dispatcher)
        //context.system.scheduler.scheduleWithFixedDelay(2000.milliseconds, 2000.milliseconds, a, InitiateNodeFailure())(context.system.dispatcher)
      })
       */

    case RunPeriodically() =>
      log.info("Run Periodically")
      //ring.values.foreach(a => {
      log.info("server11 Stabilize()")
      server11 ! Stabilize()

      Thread.sleep(2000)
      log.info("server22 Stabilize()")
      server22 ! Stabilize()

      Thread.sleep(2000)
      log.info("server33 Stabilize()")
      server33 ! Stabilize()

      Thread.sleep(2000)
      log.info("server44 Stabilize()")
      server44 ! Stabilize()

      Thread.sleep(2000)
      log.info("server11 Stabilize()")
      server11 ! Stabilize()

      Thread.sleep(2000)
      log.info("server22 Stabilize()")
      server22 ! Stabilize()

      Thread.sleep(2000)
      log.info("server33 Stabilize()")
      server33 ! Stabilize()

      Thread.sleep(2000)
      log.info("server44 Stabilize()")
      server44 ! Stabilize()

      Thread.sleep(2000)
      log.info("server11 Stabilize()")
      server11 ! Stabilize()

      Thread.sleep(2000)
      log.info("server22 Stabilize()")
      server22 ! Stabilize()

      Thread.sleep(2000)
      log.info("server33 Stabilize()")
      server33 ! Stabilize()

      Thread.sleep(2000)
      log.info("server44 Stabilize()")
      server44 ! Stabilize()

      Thread.sleep(2000)
      log.info("server11 Stabilize()")
      server11 ! Stabilize()

      Thread.sleep(2000)
      log.info("server22 Stabilize()")
      server22 ! Stabilize()

      Thread.sleep(2000)
      log.info("server33 Stabilize()")
      server33 ! Stabilize()

      Thread.sleep(2000)
      log.info("server44 Stabilize()")
      server44 ! Stabilize()

      Thread.sleep(2000)
      log.info("server11 Stabilize()")
      server11 ! Stabilize()

      Thread.sleep(2000)
      log.info("server22 Stabilize()")
      server22 ! Stabilize()

      Thread.sleep(2000)
      log.info("server33 Stabilize()")
      server33 ! Stabilize()

      Thread.sleep(2000)
      log.info("server44 Stabilize()")
      server44 ! Stabilize()

      Thread.sleep(2000)
      log.info("server11 Stabilize()")
      server11 ! Stabilize()

      Thread.sleep(2000)
      log.info("server22 Stabilize()")
      server22 ! Stabilize()

      Thread.sleep(2000)
      log.info("server33 Stabilize()")
      server33 ! Stabilize()

      Thread.sleep(2000)
      log.info("server44 Stabilize()")
      server44 ! Stabilize()

      Thread.sleep(2000)
      log.info("server11 FixFingers()")
      server11 ! FixFingers()

      Thread.sleep(2000)
      log.info("server11 FixFingers()")
      server11 ! FixFingers()

      Thread.sleep(2000)
      log.info("server11 FixFingers()")
      server11 ! FixFingers()

      Thread.sleep(2000)
      log.info("server11 FixFingers()")
      server11 ! FixFingers()

      Thread.sleep(2000)
      log.info("server22 FixFingers()")
      server22 ! FixFingers()

      Thread.sleep(2000)
      log.info("server22 FixFingers()")
      server22 ! FixFingers()

      Thread.sleep(2000)
      log.info("server22 FixFingers()")
      server22 ! FixFingers()

      Thread.sleep(2000)
      log.info("server33 FixFingers()")
      server33 ! FixFingers()

      Thread.sleep(2000)
      log.info("server33 FixFingers()")
      server33 ! FixFingers()

      Thread.sleep(2000)
      log.info("server33 FixFingers()")
      server33 ! FixFingers()

      Thread.sleep(2000)
      log.info("server44 FixFingers()")
      server44 ! FixFingers()

      Thread.sleep(2000)
      log.info("server44 FixFingers()")
      server44 ! FixFingers()

      Thread.sleep(2000)
      log.info("server44 FixFingers()")
      server44 ! FixFingers()

      Thread.sleep(2000)
      log.info("*********************** NETWORK STABLE ***********************")

      val client1 = context.system.actorOf(Props[ClientActor], "client1")
      val client2 = context.system.actorOf(Props[ClientActor], "client2")
      val movie1 = new Movie("sample1.mov")
      val movie2 = new Movie("sample2.mov")
      val movie3 = new Movie("sample3.mov")

      Thread.sleep(2000)
      client1 ! Put(movie1, server22)

      Thread.sleep(2000)
      client1 ! Put(movie2, server11)

      Thread.sleep(2000)
      client2 ! Get("sample2.mov", server22)

      Thread.sleep(2000)
      client1 ! Get("sample1.mov", server11)

    case DumpSystemState() =>
      log.info("Dumping system state now. Time is: "+new java.util.Date().getTime/1000)

      val file = new File(dumpOutput)
      val bw = new BufferedWriter(new FileWriter(file))

      bw.write("<state>\n")
      ring.values.foreach(a => {
        bw.write("<actor>\n")
        bw.write("<id>"+a.path.name+"</id>\n")

        bw.write("<fingers>\n")
        val printFuture = a ? PrintFingerTable()
        val printed: List[String] = Await.result(printFuture, timeout.duration).asInstanceOf[List[String]]

        printed.foreach(fline => {
          bw.write("<entry>\n")
          bw.write(fline)
          bw.write("</entry>\n")
        })

        bw.write("</fingers>\n")

        bw.write("</actor>\n")
      })
      bw.write("</state>\n")
      bw.close()

    case InitiateNodeFailure =>
      val nodeId = random.nextInt(ringSize)
      val failProbability = random.nextInt(100)

      //There is a 5% probability at any given time that a node fails
      if (failProbability>95) {
        log.info("[Simulated Failure] Predecessor of node with ID "+nodeId+" has failed. p="+failProbability)
        val nodeRef = ring.getOrElse(nodeId, null)
        nodeRef ! CheckPredecessor
      }

    //This callback is invoked when a node fails and the ChordSimulator needs to update the node status and remove it from the ring
    case NodeFailure(nodeId) =>
      log.info("[NodeFailure] Simulated failure, node with id: "+nodeId+" has failed. Removing it..")
      val newRing = ring.filterKeys(_ != nodeId)
      context become active(newRing.toMap)

    case SpawnServer(queue) =>
      val ip = generateIP()
      val bucket = getBucket(ip)
      val server = context.system.actorOf(Props(classOf[ServerActor], bucket, m, ringSize), bucket.toString)

      if(server22 == null)
        server22 = server
      else if (server22 != null && server33 == null)
        server33 = server
      else if (server22 != null && server33 != null)
        server44 = server

      // todo: we should pick a random node
      printSpawn(ip, bucket)
      server ! Join(ring.values.toList(0), queue, self)

    case CreateRespond(id, queue, replyTo) =>
      context become active(ring + (id -> replyTo))

      val printFuture = replyTo ? PrintFingerTable()
      val printed: List[String] = Await.result(printFuture, timeout.duration).asInstanceOf[List[String]]

      self ! SpawnServerRespond(queue)

    case JoinRespond(id, queue, replyTo) =>
      context become active(ring + (id -> replyTo))

      val printFuture = replyTo ? PrintFingerTable()
      val printed: List[String] = Await.result(printFuture, timeout.duration).asInstanceOf[List[String]]

      self ! SpawnServerRespond(queue)

    case SpawnServerRespond(queue) =>
      printRingState(ring)
      if(queue != 1) {
        self ! SpawnServer(queue - 1)
      }

  }

  def printRingState(ring: Map[Int, ActorRef]): Unit ={
    log.info("---------- RING STATE ----------")
    ring.keysIterator.foreach( k => {
      if(ring.getOrElse(k, null) != null){
        log.info("BUCKET: "+ k + " SERVER: " + ring.getOrElse(k, null).path.name)
      }
    })
  }

  def printSpawn(ip: String, bucket: Int): Unit ={
    log.info("---------- SPAWNING SERVER ----------")
    log.info("IP: " + ip)
    log.info("BUCKET: " + bucket)
  }
}


/*
val client1 = context.system.actorOf(Props[ClientActor], "client1")
val movie1 = new Movie("sample1.mov")
server1 ! Put(movie1, client1)
server1 ! Put(movie1, client1)
server1 ! Put(movie1, client1)

case RunPeriodically() =>
log.info("Run Periodically")
//

log.info("server11 Stabilize()")
server11 ! Stabilize()

Thread.sleep(2000)
log.info("server22 Stabilize()")
server22 ! Stabilize()

Thread.sleep(2000)
log.info("server33 Stabilize()")
server33 ! Stabilize()

Thread.sleep(2000)
log.info("server44 Stabilize()")
server44 ! Stabilize()

Thread.sleep(2000)
log.info("server11 Stabilize()")
server11 ! Stabilize()

Thread.sleep(2000)
log.info("server22 Stabilize()")
server22 ! Stabilize()

Thread.sleep(2000)
log.info("server33 Stabilize()")
server33 ! Stabilize()

Thread.sleep(2000)
log.info("server44 Stabilize()")
server44 ! Stabilize()

Thread.sleep(2000)
log.info("server11 Stabilize()")
server11 ! Stabilize()

Thread.sleep(2000)
log.info("server22 Stabilize()")
server22 ! Stabilize()

Thread.sleep(2000)
log.info("server33 Stabilize()")
server33 ! Stabilize()

Thread.sleep(2000)
log.info("server44 Stabilize()")
server44 ! Stabilize()

Thread.sleep(2000)
log.info("server11 Stabilize()")
server11 ! Stabilize()

Thread.sleep(2000)
log.info("server22 Stabilize()")
server22 ! Stabilize()

Thread.sleep(2000)
log.info("server33 Stabilize()")
server33 ! Stabilize()

Thread.sleep(2000)
log.info("server44 Stabilize()")
server44 ! Stabilize()

Thread.sleep(2000)
log.info("server11 Stabilize()")
server11 ! Stabilize()

Thread.sleep(2000)
log.info("server22 Stabilize()")
server22 ! Stabilize()

Thread.sleep(2000)
log.info("server33 Stabilize()")
server33 ! Stabilize()

Thread.sleep(2000)
log.info("server44 Stabilize()")
server44 ! Stabilize()

Thread.sleep(2000)
log.info("server11 Stabilize()")
server11 ! Stabilize()

Thread.sleep(2000)
log.info("server22 Stabilize()")
server22 ! Stabilize()

Thread.sleep(2000)
log.info("server33 Stabilize()")
server33 ! Stabilize()

Thread.sleep(2000)
log.info("server44 Stabilize()")
server44 ! Stabilize()

Thread.sleep(2000)
log.info("server11 FixFingers()")
server11 ! FixFingers()

Thread.sleep(2000)
log.info("server11 FixFingers()")
server11 ! FixFingers()

Thread.sleep(2000)
log.info("server11 FixFingers()")
server11 ! FixFingers()

Thread.sleep(2000)
log.info("server11 FixFingers()")
server11 ! FixFingers()

Thread.sleep(2000)
log.info("server22 FixFingers()")
server22 ! FixFingers()

Thread.sleep(2000)
log.info("server22 FixFingers()")
server22 ! FixFingers()

Thread.sleep(2000)
log.info("server22 FixFingers()")
server22 ! FixFingers()

Thread.sleep(2000)
log.info("server33 FixFingers()")
server33 ! FixFingers()

Thread.sleep(2000)
log.info("server33 FixFingers()")
server33 ! FixFingers()

Thread.sleep(2000)
log.info("server33 FixFingers()")
server33 ! FixFingers()

Thread.sleep(2000)
log.info("server44 FixFingers()")
server44 ! FixFingers()

Thread.sleep(2000)
log.info("server44 FixFingers()")
server44 ! FixFingers()

Thread.sleep(2000)
log.info("server44 FixFingers()")
server44 ! FixFingers()

//a ! CheckPredecessor()

  //context.system.scheduler.scheduleWithFixedDelay(Duration.Zero, 2000.milliseconds, a, Stabilize)(context.system.dispatcher)
  //context.system.scheduler.scheduleWithFixedDelay(Duration.Zero, 2000.milliseconds, a, CheckPredecessor)(context.system.dispatcher)
//})
 */
