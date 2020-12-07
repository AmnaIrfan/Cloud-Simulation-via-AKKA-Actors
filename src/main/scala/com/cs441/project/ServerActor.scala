package com.cs441.project

import akka.actor.{Actor, ActorRef, ActorSelection}
import akka.pattern.ask
import akka.event.Logging
import akka.util.Timeout

import scala.concurrent.duration._
import com.cs441.project.objects.{ActorRefWrapper, Movie}
import com.cs441.project.protocols.ChordProtocol.{CheckPredecessor, Create, CreateRespond, FindSuccessor, FindSuccessorID, FixFingers, FixFingersRespond, GetPredecessor, GetPredecessorRespond, Join, JoinRespond, Notify, Ping, Stabilize, StabilizeRespond}
import com.cs441.project.protocols.FileManagementProtocol.{AddFile, Get, GetFile, GetServer, Put, PutServer, RespondGet, RespondPut}
import com.cs441.project.protocols.SimulationProtocol.PrintFingerTable
import com.cs441.project.utils.{ConsistentHashing, FingerTable}

import scala.util.{Failure, Success, Try}
import scala.collection.mutable.ListBuffer
import scala.concurrent.{Await, Future}

class ServerActor(n: Int, m: Int, ringSize: Int) extends Actor{

  private val log = Logging(context.system, this)

  implicit val timeout: Timeout = 5.seconds

  def findSuccessor(nNode: ActorRef, id_t: ActorRef, successor: ActorRef, fingerTable: FingerTable): ActorRef = {
    val id_num = id_t.path.name.toInt
    val succ_num = successor.path.name.toInt
    if(belongs(n, id_num, succ_num, fingerTable.getRingSize-1, inFindSuccessor = true)) {

      log.info("FindSuccessor (IMMEDIATE) on "+n)

      log.info("node "+id_num+" is between "+n+" and "+succ_num)
      //sender() ! Future(successor)(context.dispatcher)
      return successor
    } else {
      val n1 = closestPrecedingNode(nNode, id_num, fingerTable)

      log.info("FindSuccessor (FORWARD to "+n1.path.name+") on "+n)

      //We are looping, let's assume this is the right successor
      if (n1.path.name.equals(nNode.path.name)) {
        return n1
      } else {
        //n1 ! FindSuccessor(id_t)
        val successorFuture: Future[ActorRef] = ask(n1, FindSuccessor(id_t)).mapTo[ActorRef]
        log.info("*** LOGGING *** This: "+nNode.path.name+" ("+n+") sending to: "+n1.path.name)
        val s : ActorRef = Await.result(successorFuture, timeout.duration).asInstanceOf[ActorRef]
        s
      }
    }
  }

  def findSuccessorID(nNodeID: Int, id_t: Int, successor: ActorRef, fingerTable: FingerTable, callInitiator: ActorRef): ActorRef = {
    log.info("************** SUCCESSOR OF "+n+": "+successor.path.name)
    val id_num = id_t
    val succ_num = successor.path.name
    if(belongs(n, id_num, succ_num.toInt, fingerTable.getRingSize-1, inFindSuccessor = true)) {

      log.info("FindSuccessor (IMMEDIATE) on "+n)

      log.info("node "+id_num+" is between "+n+" and "+succ_num)
      //sender() ! Future(successor)(context.dispatcher)
      return successor
    } else {
      val n1 = closestPrecedingNodeID(nNodeID, id_num, fingerTable)

      log.info("FindSuccessor ID (FORWARD to "+n1+") on "+n)

      if(n1 == n) { //todo: uscire dalla procedura senza fare niente
        log.info("*** STOP *** None of our successors knows? We are: "+nNodeID+" "+self.path.name)
        callInitiator
      } else {
        val actorRefN1 = Await.result(context.actorSelection("../"+n1).resolveOne(), timeout.duration)
        //n1 ! FindSuccessor(id_t)
        val successorFuture: Future[ActorRef] = ask(actorRefN1, FindSuccessorID(id_t, new ActorRefWrapper(callInitiator))).mapTo[ActorRef]
        log.info("*** LOGGING *** This: "+nNodeID+" ("+n+") sending to: "+n1)
        val s : ActorRef = Await.result(successorFuture, timeout.duration).asInstanceOf[ActorRef]
        s
      }
    }
  }

  def closestPrecedingNodeID(nNodeID: Int, id: Int, fingerTable: FingerTable): Int = {
    fingerTable.getEntries.reverse.foreach(e =>{
      log.info("----------- ENTRY = " +e)
      val hop = fingerTable.getHops(e)
      val successor  = fingerTable.getSuccessors(e)
      val successor_bucket = successor.path.name.toInt
      //finger[i] ∈ (n, id)
      if(belongs(n, successor_bucket, id, fingerTable.getRingSize-1, inFindSuccessor = false)){
        return successor_bucket
      }
    })
    //todo: errore
    nNodeID
  }

  def getPredecessor(predecessor: ActorRef): ActorRef = {
    if (predecessor!=null) {
      log.info("("+n+") "+"GetPredecessor() returns: " + predecessor.path.name)
      return predecessor
    } else {
      log.info("("+n+") "+"GetPredecessor() returns: null")
      return null
    }
  }

  override def receive: Receive = active(null, null, new FingerTable(n, m, ringSize, self, Array.fill[ActorRef](m)(self)), 0, Map[String, Movie]())

  private def active(predecessor: ActorRef, successor: ActorRef, fingerTable: FingerTable, next:Int, movies: Map[String, Movie]): Receive = {

    case Create(queue, replyTo) =>
      log.info("("+n+") "+"Create()")

      val predecessor = null
      val successor = self
      context become active(predecessor, successor, fingerTable, next, movies)

      replyTo ! CreateRespond(n, queue, self)

    case Join(existingNode, queue, replyTo) =>
      val predecessor = null
      val successorFuture:Future[ActorRef] = ask(existingNode, FindSuccessor(self)).mapTo[ActorRef]
      val successor: ActorRef = Await.result(successorFuture, timeout.duration)

      context become active(null,successor, fingerTable, next, movies)

      log.info("("+n+") "+"Join("+existingNode.path.name+")" + "\t" + "pred: nil" + "\t" + "succ: " + successor.path.name)

      replyTo ! JoinRespond(n, queue, self)

    case PrintFingerTable() =>
      val outList = printFingerTable(fingerTable, self.path.name)
      sender() ! outList

    case FindSuccessor(id_t) =>
      sender() ! findSuccessor(self, id_t, successor, fingerTable)

    case FindSuccessorID(id_t, initiator) =>
      val in = initiator.getActorRef()
      sender() ! findSuccessorID(self.path.name.toInt, id_t, successor, fingerTable, in)

    case Stabilize() =>

      //Message sent locally
      if (successor.path.name.equals(self.path.name)) {
        val x = getPredecessor(predecessor)

        //Local predecessor is null
        if (x!=null) {
          val x_bucket = x.path.name.toInt
          if (belongs(n, x_bucket, successor.path.name.toInt, fingerTable.getRingSize - 1, inFindSuccessor = false)) {
            context become active(predecessor, x, fingerTable, next, movies)
          }
          log.info("("+n+") "+"Stabilize() LOCAL x: " + x.path.name)
        }

        self ! StabilizeRespond()

      } else {
          //Send message to successor and query its predecessor
          log.info("("+n+") "+"Stabilize() REMOTE a) call GetPredecessor \t succ: " + successor.path.name + "\t replyTo: "+ n)
          successor ! GetPredecessor(self)
        }

    case GetPredecessor(replyTo: ActorRef) =>
      val res = getPredecessor(predecessor)

      if (res==null) {
        log.info("("+n+") "+"Stabilize() REMOTE b) in GetPredecessor \t predecessor: null \t replyTo: "+replyTo.path.name)
        replyTo ! GetPredecessorRespond(new ActorRefWrapper(null))
      } else {
        log.info("("+n+") "+"Stabilize() REMOTE b) in GetPredecessor \t predecessor: " + res.path.name + "\t replyTo: "+replyTo.path.name)
        replyTo ! GetPredecessorRespond(new ActorRefWrapper(res))
      }

    case GetPredecessorRespond(wrapper: ActorRefWrapper) => {

      val x = wrapper.getActorRef()

      //log.info("("+n+") "+"Stabilize() REMOTE c) Have successor's predecessor \t succPres: " + x.path.name)
      //todo: we are returning ourselves instead of null
      //if (x.path.name.equals(self.path.name)) {
      if (x==null) {
        log.info("("+n+")" + "Stabilize() REMOTE c) Predecessor is null")
      } else {
        val x_bucket = x.path.name.toInt
        if (belongs(n, x_bucket, successor.path.name.toInt, fingerTable.getRingSize - 1, inFindSuccessor = false)) {
          context become active(predecessor, x, fingerTable, next, movies)
          log.info("("+n+") " + "Stabilize() REMOTE c) Predecessor is NOT null -> setting n's succ: " + x.path.name)
        }
          log.info("("+n+") " + "Stabilize() REMOTE c) Predecessor is NOT null and not in interval. ")
      }

      self ! StabilizeRespond()
    }

    case StabilizeRespond() =>
      log.info("("+n+") "+"Stabilize() -> ("+successor.path.name+") Notify("+self.path.name+")")

      successor ! Notify(self)

    case Notify(n1: ActorRef) =>

      //if (id ∈ (n, successor]) -> belongs(n, id, succ)
      //n′ ∈ (predecessor, n)
      val n1_bucket = n1.path.name.toInt

      if (predecessor!=null) {

        val pred_bucket = predecessor.path.name.toInt

        if (belongs(pred_bucket, n1_bucket, n, fingerTable.getRingSize-1, inFindSuccessor = false)) {
          log.info("("+n+")" + " Notify("+n1_bucket+") pred was not null, pred: " + n1_bucket + "\t succ: " + successor.path.name)
          context become active(n1, successor, fingerTable, next, movies)
        }

      } else {
        log.info("("+n+")" + " Notify("+n1_bucket+") pred was null, pred: " + n1_bucket + "\t succ: " + successor.path.name)
        context become active(n1, successor, fingerTable, next, movies)
      }

    case FixFingers() =>
      log.info("FixFingers on "+n+" self="+self.path.name)

      val next1 = next + 1
        if(next1 >= fingerTable.getM){

          log.info("---------- FixFingers FINGER TABLE ----------")
          val fingerNew = updateFinger(n, next1, successor, fingerTable)
          printFingerTable(fingerNew, self.path.name)
          context become active(predecessor, successor, fingerNew, 0, movies)
          log.info("---------- FixFingers BLOCK END FINGER TABLE ----------")
        } else{
          log.info("---------- FixFingers FINGER TABLE ----------")
          val fingerNew = updateFinger(n, next1, successor, fingerTable)
          printFingerTable(fingerNew, self.path.name)
          context become active(predecessor, successor, fingerNew, next1, movies)
          log.info("---------- FixFingers BLOCK END FINGER TABLE ----------")
        }
      self ! FixFingersRespond()
    case FixFingersRespond() =>
      printFingerTable(fingerTable, self.path.name)

    case CheckPredecessor() => {
      log.info("CheckPredecessor on "+n)
      //With a certain distribution return null
    }

    case Ping() =>
      log.info("Ping on "+n)
      sender() ! true

    case GetServer(name, replyTo) =>
      val file_id = ConsistentHashing.getBucket(name)
/*
      val srcFuture: Future[ActorRef] = ask(self, FindSuccessorID(file_id, new ActorRefWrapper(self))).mapTo[ActorRef]
      val src : ActorRef = Await.result(srcFuture, timeout.duration)

*/
      val src: ActorRef = findSuccessorID(n, file_id, successor, fingerTable, self)

      src ! GetFile(name, replyTo)

    case PutServer(file, replyTo) =>
      val file_id = ConsistentHashing.getBucket(file.getName)
      /*
      val destFuture: Future[ActorRef] = ask(self, FindSuccessorID(file_id, new ActorRefWrapper(self))).mapTo[ActorRef]
      val dest : ActorRef = Await.result(destFuture, timeout.duration)
      */

      val dest: ActorRef = findSuccessorID(n, file_id, successor, fingerTable, self)


      dest ! AddFile(file, replyTo)

    case AddFile(file, replyTo) =>
      context become active(predecessor, successor, fingerTable, next, movies + (file.getName -> file))
      replyTo ! RespondPut(file.getName, Some(true), n)

    case GetFile(name, replyTo) =>
      val file = movies.get(name)
      replyTo ! RespondGet(name, file, n)
  }

  def updateFinger(n: Int, next: Int, successor: ActorRef, fingerTable: FingerTable): FingerTable = {
    log.info("UPDATING FINGER...")
    // getHops before was get
    val succN = findSuccessorID(n, fingerTable.getHops(next-1), successor, fingerTable, self)
    log.info("************ SUCCESSOR of "+fingerTable.getHops(next-1)+" ************: "+succN.path.name+", NEXT = "+next)
    fingerTable.updateSuccessor(next-1, succN)
  }

  //if (id ∈ (n, successor]) -> belongs(n, id, succ)

  /*
 def belongs(n: Int, id: Int, succ: Int, max: Int) : Boolean = {

  if (n==succ) {
    return true
  }

    //We are crossing 0
   if (n > succ) {
     //Search counterclockwise when going through zero
     if ((id >= n && id<=max) || id>=0 && id<=succ) {
       return true
     }
     false
   } else {
     if (id > n && id<=succ) {
       return true
     }
     false
   }
  }
  */

// FLAG inFindSuccessor ---> True if called from FindSuccessor, False if called anywhere else
  def belongs(n: Int, id: Int, succ: Int, max: Int, inFindSuccessor: Boolean) : Boolean = {
    val res = isInRange(id, n, succ, inFindSuccessor)
    log.info("BELONGS: is " + id + " between " + n + " and " + succ + " ? -> " + res.toString)
    //log.info("belongs: n="+n+" id="+id+" succ="+succ+" max="+max+" -> "+res)
    return res
  }

  def isInRange(idToCheck: Int, lowerBound: Int, upperBound: Int, inFindSuccessor: Boolean): Boolean = {
    if(inFindSuccessor) {
      if ((lowerBound > upperBound) && ((idToCheck > lowerBound) || (idToCheck < upperBound)))
        true
      else if ((lowerBound < upperBound) && (idToCheck > lowerBound) && (idToCheck <= upperBound))
        true
      else if (lowerBound == upperBound)
        true
      else
        false
    }
    else {
      if ((lowerBound > upperBound) && ((idToCheck > lowerBound) || (idToCheck < upperBound)))
        true
      else if ((lowerBound < upperBound) && (idToCheck > lowerBound) && (idToCheck < upperBound))
        true
      else if (lowerBound == upperBound)
        true
      else
        false
    }
  }

  def closestPrecedingNode(nNode: ActorRef, id: Int, fingerTable: FingerTable): ActorRef = {
    fingerTable.getEntries.reverse.foreach(e =>{
      val hop = fingerTable.getHops(e)
      val successor  = fingerTable.getSuccessors(e)
      val successor_bucket = successor.path.name.toInt
      //finger[i] ∈ (n, id)
      //todo: probably this is wrong (?) belongs: n=108 id=108 succ=129 max=255 -> false
      if(belongs(n, successor_bucket, id, fingerTable.getRingSize-1, false)){
          return successor
      }
    })
    nNode
  }

  def printFingerTable(fingerTable: FingerTable, ip: String): List[String] = {

    var listBuffer = new ListBuffer[String]()
    val stringBuilder: StringBuilder = new StringBuilder

    stringBuilder.append("---------- FINGER TABLE " + ip + " ----------")
    val out = printFingerTableImpl(stringBuilder.toString(), listBuffer, fingerTable, fingerTable.getEntries.length-1)

    log.info(out)

    return listBuffer.toList
  }

  def printFingerTableImpl(string: String, listBuffer: ListBuffer[String], fingerTable: FingerTable, entries:Int, current: Int = 0): String ={
    if(current > entries){
      return string
    }
    val i = fingerTable.getEntries(current)
    val hops = fingerTable.getHops(current)
    val succ = fingerTable.getSuccessors(current).path.name

    listBuffer.append("<i>"+i.toString+"</i><hop>"+hops.toString+"</hop><succ>"+succ.toString+"</succ>")

    val out = string ++ ("\n"+ i + "\t" + hops + "\t\t" + succ)
    return printFingerTableImpl(out, listBuffer, fingerTable, entries, current + 1)
  }

}
