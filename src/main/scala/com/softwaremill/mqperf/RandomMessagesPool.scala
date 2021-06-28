package com.softwaremill.mqperf

import java.util.concurrent.atomic.AtomicInteger
import scala.util.Random

object RandomMessagesPool {
  final val DefaultMessagesPoolSize = 10000

  def apply(messageLength: Int, poolSize: Int = DefaultMessagesPoolSize): RandomMessagesPool = {
    if (messageLength <= 0 || poolSize <= 0) {
      throw new IllegalArgumentException(s"Invalid message pool parameters provided. Message length: $messageLength, pool size: $poolSize")
    }

    val messages = (1 to poolSize).map(_ => Random.alphanumeric.take(messageLength).mkString).toList
    RandomMessagesPool(messages)
  }
}

case class RandomMessagesPool(messages: List[String]) {
  private val currentIndex = new AtomicInteger(0)
  private val messagesVector = messages.toVector

  def nextMessage(): String = messagesVector(currentIndex.getAndIncrement() % messagesVector.size)
}
