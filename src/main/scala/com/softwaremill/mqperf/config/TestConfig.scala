package com.softwaremill.mqperf.config

import com.typesafe.config.{Config, ConfigFactory}

case class TestConfig(
    name: String,
    mqType: String,
    senderThreads: Int,
    msgCountPerThread: Int,
    msgSize: Int,
    maxSendMsgBatchSize: Int,
    receiverThreads: Int,
    receiveMsgBatchSize: Int,
    mqConfig: Config
) {

  def mqClassName = s"com.softwaremill.mqperf.mq.${mqType}Mq"
}

object TestConfig {

  def from(config: Config): TestConfig = TestConfig(
    name = config.getString("name"),
    mqType = config.getString("mq_type"),
    senderThreads = config.getInt("sender_threads"),
    msgCountPerThread = config.getInt("msg_count_per_thread"),
    msgSize = config.getInt("msg_size"),
    maxSendMsgBatchSize = config.getInt("max_send_msg_batch_size"),
    receiverThreads = config.getInt("receiver_threads"),
    receiveMsgBatchSize = config.getInt("receive_msg_batch_size"),
    mqConfig = config.getConfigOpt("mq").getOrElse(ConfigFactory.empty())
  )

  val HostPortPattern = """([^:\s]+):?([0-9]*)""".r

  def parseHostPort(from: String): (String, Option[Int]) = from match {
    case HostPortPattern(host, "") => (host, None)
    case HostPortPattern(host, port) => (host, Some(port.toInt))
    case _ => throw new IllegalArgumentException(s"Invalid host address: $from")
  }

}
