package com.softwaremill.mqperf.mq

import com.typesafe.config.Config

class DummyMq(val config: Config) extends Mq {
  override type MsgId = String

  override def createSender() = new MqSender {
    override def send(msgs: List[String]) {}
  }

  override def createReceiver() = new MqReceiver {
    override def receive(maxMsgCount: Int) = Nil

    override def ack(ids: List[MsgId]) {}
  }
}
