package com.softwaremill.mqperf.mq

import com.softwaremill.mqperf.config.TestConfig

trait Mq {
  type MsgId

  trait MqSender {
    /**
     * Synchronous - must wait for the messages to be sent
     */
    def send(msgs: List[String])

    def close() {}
  }

  trait MqReceiver {
    def receive(maxMsgCount: Int): List[(MsgId, String)]

    /**
     * Can be asynchronous
     */
    def ack(ids: List[MsgId])

    def close() {}
  }

  def createSender(): MqSender
  def createReceiver(): MqReceiver
}

object Mq {
  def instantiate(testConfig: TestConfig) = {
    Class.forName(testConfig.mqClassName).getConstructors()(0).newInstance(testConfig.mqConfigMap).asInstanceOf[Mq]
  }
}