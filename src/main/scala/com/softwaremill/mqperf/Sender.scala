package com.softwaremill.mqperf

object Sender extends App {
  println("Hello world! Args: " + args.toList)
  println("& bye!")

  new TestConfigOnS3().whenChanged { content =>
    println("Changed!!!")
    println(content)
  }
}
