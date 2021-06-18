package com.softwaremill.mqperf

import com.softwaremill.mqperf.config.TestConfig
import org.scalatest.{FlatSpec, Matchers}

class MsgTest extends FlatSpec with Matchers {
  it should "add and extract timestamp" in {
    val prefix = Msg.prefix(TestConfig("", "", 0, 0, 100, 0, 0, 0, Nil, "", null))
    val now = System.currentTimeMillis()
    val extracted = Msg.extractTimestamp(Msg.addTimestamp(prefix))
    now should be <= extracted
    extracted shouldBe <= (now + 10) // tolerance
  }
}
