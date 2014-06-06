package com.softwaremill.mqperf.util

import scala.annotation.tailrec

trait Retry {
  @tailrec
  final def retry[T](attempts: Int, betweenAttempts: () => Unit = () => ())(block: => T): T = {
    try {
      block
    } catch {
      case e: Exception => if (attempts == 1) throw e else {
        betweenAttempts()
        retry(attempts - 1)(block)
      }
    }
  }
}

object Retry extends Retry
