package com.softwaremill.mqperf.mq

import com.softwaremill.mqperf.config.TestConfig

trait Mq {
  type MsgId

  /**
   * Synchronous - must wait for the messages to be sent
   */
  def send(msgs: List[String])

  def receive(maxMsgCount: Int): List[(MsgId, String)]

  /**
   * Can be asynchronous
   */
  def ack(ids: List[MsgId])
}

object Mq {
  def instantiate(testConfig: TestConfig) = {
    Class.forName(testConfig.mqClassName).newInstance().asInstanceOf[Mq]
  }
}