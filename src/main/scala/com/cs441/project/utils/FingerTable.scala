package com.cs441.project.utils


import akka.actor.ActorRef
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.math.pow

class FingerTable(id: Int, m: Int, ringSize: Int, server: ActorRef, successors: Array[ActorRef]) {
  // Initialize Logger
  private val LOG: Logger = LoggerFactory.getLogger(getClass)

  // Initialize FingerTable arrays
  private val i: ArrayBuffer[Int] = initEntries(m-1)
  private val hops: ArrayBuffer[Int] =  initHops(id, i)
  //private val successors: ArrayBuffer[ActorRef] =  initSuccessors(id, i)

  /**
   * Initialize entries array with m-1 entries
   *
   * @param m m power
   * @param i builder for array containing entries indices
   * @return array containing entries indices
   */
  @scala.annotation.tailrec
  private def initEntries(m: Int, i:ArrayBuffer[Int] = new ArrayBuffer[Int]()): ArrayBuffer[Int] ={
    if(m == -1){
      return i.reverse
    }
    i.addOne(m)
    initEntries(m-1, i)
  }

  /**
   * Initialize hops array
   *
   * @param id id of current node
   * @param i array of entries indices
   * @return array containing hops
   */
  private def initHops(id: Int, i: ArrayBuffer[Int]): ArrayBuffer[Int] = {
    i.map(entry => calcHop(id, entry))
  }

  /**
   * Calculate exponential hops given the Chord formulation
   *
   * @param id id of current node
   * @param i i-th entry
   * @return hop
   */
  private def calcHop(id: Int, i: Int): Int ={
    ((id + pow(2, i)) % ringSize).toInt
  }

  /**
   * Initialize successors column to have each entry equal to the node itself
   *
   * @param id id of current node
   * @param i array of entries indices
   * @return initial array of successors
   */
  private def initSuccessors(id: Int, i: ArrayBuffer[Int]): ArrayBuffer[ActorRef] = {
    i.map(entry => server)
  }

  /**
   * Get entries indices
   *
   * @return entries indices
   */
  def getEntries: List[Int] = {
    i.toList
  }

  /**
   * Get hops
   *
   * @return hops
   */
  def getHops: List[Int] = {
    hops.toList
  }

  /**
   * Get successors
   *
   * @return successors
   */
  def getSuccessors: List[ActorRef] = {
    successors.toList
  }


  /**
   * Update successor for a given entry
   *
   * @param i entry index
   * @param successor new successor
   */

  def updateSuccessor(i:Int, successor: ActorRef) : FingerTable = {
    val newSuccs = new ArrayBuffer[ActorRef]()
    newSuccs.addAll(successors)
    newSuccs.update(i, successor)
    val newTable = new FingerTable(id, m, ringSize, server, newSuccs.toArray)
    newTable
  }

  def getRingSize:Int={
    ringSize
  }
  def getM:Int={
    m
  }
}
