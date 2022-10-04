package mqperf

import java.time.Clock

object Timestamp {
  private val TimestampLength = 13

  def add(message: String, clock: Clock): String = {
    val messagePrefix = message.dropRight(TimestampLength)
    messagePrefix + clock.millis().toString
  }

  def extract(msg: String): Long = msg.takeRight(TimestampLength).toLong
}
