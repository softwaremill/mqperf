package com.softwaremill.mqperf

import com.softwaremill.mqperf.config.TestConfig

import scala.util.Random

object Msg {
  private val TimestampLength = 13

  def prefix(testConfig: TestConfig): String = {
    val prefixLength = testConfig.msgSize - TimestampLength
    if (prefixLength <= 0)
      ""
    else
      List.fill(prefixLength)(Random.nextPrintableChar()).mkString
  }

  def addTimestamp(prefix: String): String = prefix + System.currentTimeMillis().toString

  def extractTimestamp(msg: String): Long = msg.takeRight(TimestampLength).toLong
}
