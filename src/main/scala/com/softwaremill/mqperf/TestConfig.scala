package com.softwaremill.mqperf

import org.json4s._
import org.json4s.native._

case class TestConfig(
  senderThreads: Int,
  msgCountPerThread: Int,
  msgSize: Long,
  maxSendMsgBatchSize: Int,
  receiverThreads: Int,
  receiveMsgBatchSize: Int)

object TestConfig {
  def from(json: String) = {
    val parsed = parseJson(json)
    val tcs = for {
      JObject(fields) <- parsed
      JField("sender_threads", JInt(senderThreads)) <- fields
      JField("msg_count_per_thread", JInt(msgCountPerThread)) <- fields
      JField("msg_size", JInt(msgSize)) <- fields
      JField("max_send_msg_batch_size", JInt(maxSendMsgBatchSize)) <- fields
      JField("receiver_threads", JInt(receiverThreads)) <- fields
      JField("receive_msg_batch_size", JInt(receiveMsgBatchSize)) <- fields
    } yield TestConfig(
      senderThreads.intValue(), msgCountPerThread.intValue(), msgSize.longValue(),
      maxSendMsgBatchSize.intValue(), receiverThreads.intValue(), receiveMsgBatchSize.intValue())

    tcs.headOption.getOrElse(throw new IllegalArgumentException(s"Invalid json: $json"))
  }
}