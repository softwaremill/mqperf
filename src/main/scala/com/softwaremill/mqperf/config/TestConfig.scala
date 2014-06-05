package com.softwaremill.mqperf.config

import org.json4s._
import org.json4s.native._

case class TestConfig(
  name: String,
  mqType: String,
  senderThreads: Int,
  msgCountPerThread: Int,
  msgSize: Int,
  maxSendMsgBatchSize: Int,
  receiverThreads: Int,
  receiveMsgBatchSize: Int) {

  def mqClassName = s"com.softwaremill.mqperf.mq.${mqType}Mq"
}

object TestConfig {
  def from(json: String): TestConfig = {
    val parsed = parseJson(json)
    val tcs = for {
      JObject(fields) <- parsed
      JField("name", JString(name)) <- fields
      JField("mq_type", JString(mqType)) <- fields
      JField("sender_threads", JInt(senderThreads)) <- fields
      JField("msg_count_per_thread", JInt(msgCountPerThread)) <- fields
      JField("msg_size", JInt(msgSize)) <- fields
      JField("max_send_msg_batch_size", JInt(maxSendMsgBatchSize)) <- fields
      JField("receiver_threads", JInt(receiverThreads)) <- fields
      JField("receive_msg_batch_size", JInt(receiveMsgBatchSize)) <- fields
    } yield TestConfig(
      name, mqType,
      senderThreads.intValue(), msgCountPerThread.intValue(), msgSize.intValue(),
      maxSendMsgBatchSize.intValue(), receiverThreads.intValue(), receiveMsgBatchSize.intValue())

    tcs.headOption.getOrElse(throw new IllegalArgumentException(s"Invalid json: $json"))
  }
}