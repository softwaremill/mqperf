package com.softwaremill.mqperf.config

import com.fasterxml.uuid.{EthernetAddress, Generators}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging

import scala.collection.JavaConverters._

case class TestConfig(
    name: String,
    mqType: String,
    senderThreads: Int,
    msgCountPerThread: Int,
    msgSize: Int,
    maxSendMsgBatchSize: Int,
    receiverThreads: Int,
    receiveMsgBatchSize: Int,
    brokerHosts: List[String],
    nodeId: String,
    mqConfig: Config
) {

  def mqClassName = s"com.softwaremill.mqperf.mq.${mqType}Mq"
}

object TestConfig extends StrictLogging {

  def load(): TestConfig = {
    val nodeId = Generators.timeBasedGenerator(EthernetAddress.fromInterface()).generate().node().toString
    val runId = sys.env.getOrElse("RUN_ID", throw new IllegalStateException("No RUN_ID defined in the environment!"))
    val config = from(nodeId, runId, ConfigFactory.load())
    logger.info("Using config: " + config)
    config
  }

  private[config] def from(nodeId: String, runId: String, config: Config): TestConfig = TestConfig(
    name = config.getString("name").replaceAll("\\$runid", runId),
    mqType = config.getString("mq_type"),
    senderThreads = config.getInt("sender_threads"),
    msgCountPerThread = config.getInt("msg_count_per_thread"),
    msgSize = config.getInt("msg_size"),
    maxSendMsgBatchSize = config.getInt("max_send_msg_batch_size"),
    receiverThreads = config.getInt("receiver_threads"),
    receiveMsgBatchSize = config.getInt("receive_msg_batch_size"),
    brokerHosts = config.getStringList("broker_hosts").asScala.toList,
    nodeId = nodeId,
    mqConfig = config.getConfigOpt("mq").getOrElse(ConfigFactory.empty())
  )

  private val HostPortPattern = """([^:\s]+):?([0-9]*)""".r

  def parseHostPort(from: String): (String, Option[Int]) = from match {
    case HostPortPattern(host, "") => (host, None)
    case HostPortPattern(host, port) => (host, Some(port.toInt))
    case _ => throw new IllegalArgumentException(s"Invalid host address: $from")
  }

}
