package com.cs441.project.utils

object GenerateIP {
  def generateIP(): String ={
    val r = scala.util.Random
    val a = r.nextInt(255)+1
    val b = r.nextInt(255)+1
    val c = r.nextInt(255)+1
    val d = r.nextInt(255)+1
    a + "." + b + "." + c + "." + d
  }
}
