package com.cs441.project.Quickstart

import org.ddahl.rscala.RClient

object RQuickstart {
  def main(args: Array[String]): Unit = {

    val R = RClient()

    val a = R.evalD1("rnorm(8)")

    a.foreach(x => println(x))

  }
}
