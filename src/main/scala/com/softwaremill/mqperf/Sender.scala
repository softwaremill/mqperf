package com.softwaremill.mqperf

import com.codahale.metrics.{Meter, Timer}
import com.softwaremill.mqperf.config.{TestConfig, TestConfigOnS3}
import com.softwaremill.mqperf.mq.Mq
import com.typesafe.scalalogging.StrictLogging

import scala.util.Random

object Sender extends App {
  val timestampLength = 13

  println("Starting sender...")
  TestConfigOnS3.create(args).whenChanged { testConfig =>
    println(s"Starting test (sender) with config: $testConfig")

    val mq = Mq.instantiate(testConfig)
    val report = new DynamoReportResults(testConfig.name)
    val sr = new SenderRunnable(
      mq, report,
      testConfig.mqType, msgPrefix(testConfig),
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

  def msgPrefix(testConfig: TestConfig): String = {
    val prefixLength = testConfig.msgSize - timestampLength
    if (prefixLength <= 0)
      ""
    else
      "0" * prefixLength
  }
}

class SenderRunnable(mq: Mq, reportResults: ReportResults, mqType: String,
    msgPrefix: String, msgCount: Int, maxSendMsgBatchSize: Int) extends Runnable with StrictLogging {

  override def run() = {
    val mqSender = mq.createSender()
    try {
      var leftToSend = msgCount
      logger.info(s"Sending $leftToSend messages")
      while (leftToSend > 0) {
        val batchSize = math.min(leftToSend, Random.nextInt(maxSendMsgBatchSize) + 1)
        val fullMsg = mq.msgWithTimestamp(msgPrefix)
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

  private def doSend(mqSender: mq.MqSender, msgTimer: Timer, meter: Meter, start: Long) {
  }
}
