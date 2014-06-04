package com.softwaremill.mqperf.mq

class DummyMq extends Mq {
  override type MsgId = String

  override def send(msgs: List[String]) {}

  override def receive(maxMsgCount: Int) = Nil

  override def ack(ids: List[MsgId]) {}
}
