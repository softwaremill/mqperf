package com.softwaremill.mqperf.mq

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
