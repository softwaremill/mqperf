package com.softwaremill.mqperf

import com.softwaremill.mqperf.config.TestConfig
import com.softwaremill.mqperf.mq.Mq
import com.typesafe.scalalogging.StrictLogging

import scala.util.Random

object Sender extends App {
  println("Starting sender...")
  val testConfig = TestConfig.load()

  val mq = Mq.instantiate(testConfig)
  val sr = new SenderRunnable(
    mq,
    testConfig.mqType, Msg.prefix(testConfig),
    testConfig.msgCountPerThread, testConfig.maxSendMsgBatchSize
  )

  val threads = (1 to testConfig.senderThreads).map { _ =>
    val t = new Thread(sr)
    t.start()
    t
  }

  threads.foreach(_.join())

  mq.close()
}

class SenderRunnable(mq: Mq, mqType: String,
  msgPrefix: String, msgCount: Int, maxSendMsgBatchSize: Int) extends Runnable with StrictLogging {

  override def run() = {
    val mqSender = mq.createSender()
    try {
      var leftToSend = msgCount
      logger.info(s"Sending $leftToSend messages")
      while (leftToSend > 0) {
        val batchSize = math.min(leftToSend, Random.nextInt(maxSendMsgBatchSize) + 1)
        val fullMsg = Msg.addTimestamp(msgPrefix)
        val batch = List.fill(batchSize)(fullMsg)
        logger.debug("Sending batch")
        mqSender.send(batch)
        leftToSend -= batchSize
      }
    }
    finally {
      mqSender.close()
    }
  }
}
