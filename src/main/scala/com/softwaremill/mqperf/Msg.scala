package com.softwaremill.mqperf

import com.softwaremill.mqperf.util.{Clock, RealClock}

object Msg {
  private[mqperf] val TimestampLength = 13

  def addTimestamp(message: String, clock: Clock = RealClock): String = {
    val messagePrefix = message.dropRight(Msg.TimestampLength)
    messagePrefix + clock.currentTimeMillis().toString
  }

  def extractTimestamp(msg: String): Long = msg.takeRight(TimestampLength).toLong
}
