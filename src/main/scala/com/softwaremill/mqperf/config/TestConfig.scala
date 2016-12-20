package com.softwaremill.mqperf.config

import com.typesafe.config.Config

case class TestConfig(name: String,
                      mqType: String,
                      senderThreads: Int,
                      msgCountPerThread: Int,
                      msgSize: Int,
                      maxSendMsgBatchSize: Int,
                      receiverThreads: Int,
                      receiveMsgBatchSize: Int,
                      mqConfigMap: Map[String, String]) {

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
    mqConfigMap = readMqSpecificConfig(config)
  )

  private def readMqSpecificConfig(config: Config): Map[String, String] = {
    import scala.collection.JavaConverters._
    val mqConfigPairs = for {
      mqSpecificConf <- config.getConfigOpt("mq").toSeq
      mqSpecificConfEntry <- mqSpecificConf.entrySet().asScala
    } yield {
      mqSpecificConfEntry.getKey -> mqSpecificConf.getString(mqSpecificConfEntry.getKey)
    }
    mqConfigPairs.toMap
  }

}
