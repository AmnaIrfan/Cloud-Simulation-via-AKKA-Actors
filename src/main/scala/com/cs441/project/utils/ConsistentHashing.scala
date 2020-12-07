package com.cs441.project.utils

import java.math.BigInteger
import java.security.MessageDigest
import java.util

import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.{Logger, LoggerFactory}

object ConsistentHashing {
  // Initialize Logger
  val LOG: Logger = LoggerFactory.getLogger(getClass)
  // Loading Config
  val settings: Config = ConfigFactory.load()
  // Get the ring size from the conf
  val ringSize: Int = settings.getInt("Simulator.ring_size")

  /**
   * Calculate the MD5 hash of a given string
   *
   * @param s input string
   * @return hash
   */
  def md5(s: String): Array[Byte] = { MessageDigest.getInstance("MD5").digest(s.getBytes) }

  /**
   * Calculate the bucket of a given string
   *
   * @param s input string
   * @return bucket
   */
    /*

     def getBucket(s:String):Int = {
    val hashValue = md5(s)
    val number = new BigInteger(hashValue)
    val bucket = number.mod(new BigInteger((ringSize-1).toString))
    bucket.intValue()
  }
     */

    val m: util.HashMap[String, Int] = new util.HashMap[String, Int]()

    var count:Int = -1
    def getBucket(s:String):Int = {

      if(m.containsKey(s)) {
        return m.get(s)
      } else {
        count=count+1
        m.put(s, count)
        return count
      }
    }

}
