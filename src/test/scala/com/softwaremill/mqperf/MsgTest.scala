package com.softwaremill.mqperf

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MsgTest extends AnyFlatSpec with Matchers {
  it should "add and extract timestamp" in {
    // given
    val message = "AG%R$WG%$##T%%#TG"
    val now = System.currentTimeMillis()
    
    // when
    val messageWithTimestamp = Msg.addTimestamp(message)
    val extracted = Msg.extractTimestamp(messageWithTimestamp)
    
    // then
    now should be <= extracted
    extracted shouldBe <= (now + 10) // tolerance
  }
}
