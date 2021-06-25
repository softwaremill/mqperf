package com.softwaremill.mqperf

import scala.util.Random

object MessagesPool {
  final val DEFAULT_MESSAGES_POOL_SIZE = 10000

  def generatePoolOfRandomMessageOfLengthN(messageLength: Int, poolSize: Int = DEFAULT_MESSAGES_POOL_SIZE): MessagesPool = {
    if (messageLength <= 0 || poolSize <= 0) {
      throw new IllegalArgumentException(s"Invalid message pool parameters provided. Message length: $messageLength, pool size: $poolSize")
    }

    val messages = (1 to poolSize).map(_ => Random.alphanumeric.take(messageLength).mkString).toList
    MessagesPool(messages)
  }
}

case class MessagesPool(messages: List[String]) {
  private val nextMessageIndex = Iterator.iterate(0)(currentIndex => (currentIndex + 1) % messages.size)

  def nextMessage(): String = messages(nextMessageIndex.next())
}
