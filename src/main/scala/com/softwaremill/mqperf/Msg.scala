package com.softwaremill.mqperf

object Msg {
  private val TimestampLength = 13

  def addTimestamp(prefix: String): String = prefix + System.currentTimeMillis().toString

  def extractTimestamp(msg: String): Long = msg.takeRight(TimestampLength).toLong
}
