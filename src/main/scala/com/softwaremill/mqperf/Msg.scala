package com.softwaremill.mqperf

import com.softwaremill.mqperf.config.TestConfig

object Msg {
  private val TimestampLength = 13

  def prefix(testConfig: TestConfig): String = {
    val prefixLength = testConfig.msgSize - TimestampLength
    if (prefixLength <= 0)
      ""
    else
      "0" * prefixLength
  }

  def addTimestamp(prefix: String): String = prefix + System.currentTimeMillis().toString

  def extractTimestamp(msg: String): Long = msg.dropWhile(_ == '0').toLong
}
