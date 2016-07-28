package com.josiahebhomenye.testharness

import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

/**
  * Created by jay on 22/07/2016.
  */
object Main {

  def main(args: Array[String]){
    implicit val ec = ExecutionContext.global
    val configure = ConfigFactory.load()

    Servers(configure).foreach{ server =>
      server.run.onComplete{
        case Success(_) => println(s"${server.name} test harness, successfully started, listening on port: ${server.port}")
        case Failure(t) =>
          println(s"unable to start server: ${server.name}")
          t.printStackTrace()
          System.exit(0)
      }
    }


  }

}
