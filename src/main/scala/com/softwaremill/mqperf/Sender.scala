package com.softwaremill.mqperf

object Sender extends App {
  println("Hello world! Args: " + args.toList)
  println("& bye!")

  new S3TestConfig().whenChanged { content =>
    println("Changed!!!")
    println(content)
  }
}
