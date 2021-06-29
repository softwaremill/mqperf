package com.softwaremill.mqperf

import com.softwaremill.mqperf.util.Clock
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor2}

class MsgTest extends AnyFlatSpec with Matchers with TableDrivenPropertyChecks {

  val messages: TableFor2[String, Int] = Table(
    ("message", "expectedLength"),
    (List.fill(100)("a").mkString, 100),
    (List.fill(20)("a").mkString, 20),
    (List.fill(4)("a").mkString, 13),
    (List.fill(Msg.TimestampLength)("a").mkString, 13)
  )
  
  forAll(messages) { (message: String, expectedLength: Int) =>
    it should s"format message with timestamp with expected message length of $expectedLength for '$message''" in {
      // given
      val prefixLength = Math.max(message.length - Msg.TimestampLength, 0)

      // when
      val formattedMessage = Msg.addTimestamp(message)

      // then
      formattedMessage.length shouldEqual expectedLength
      formattedMessage.take(prefixLength) shouldEqual message.take(prefixLength)
    }
  }
  
  it should "properly extract timestamp from the formatted message" in {
    // given
    val message = "AG%R$WG%$##T%%#TG"
    val timeMillis = 1624901673857L
    
    val clock = new Clock {
      override def nanoTime(): Long = 0

      override def currentTimeMillis(): Long = timeMillis
    }

    // when
    val messageWithTimestamp = Msg.addTimestamp(message, clock)
    val extractedTimestamp = Msg.extractTimestamp(messageWithTimestamp)
    
    // then
    extractedTimestamp shouldEqual timeMillis
  }
}
