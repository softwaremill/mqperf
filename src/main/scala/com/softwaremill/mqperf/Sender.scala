package com.softwaremill.mqperf

import com.codahale.metrics.{Meter, Timer}
import com.softwaremill.mqperf.config.TestConfigOnS3
import com.softwaremill.mqperf.mq.Mq
import com.typesafe.scalalogging.StrictLogging

import scala.util.Random

object Sender extends App {
  println("Starting sender...")
  TestConfigOnS3.create(args).whenChanged { testConfig =>
    println(s"Starting test (sender) with config: $testConfig")

    val mq = Mq.instantiate(testConfig)
    val report = new ReportResults(testConfig.name)
    val sr = new SenderRunnable(
      mq, report,
      testConfig.mqType, "0" * testConfig.msgSize,
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
}

class SenderRunnable(mq: Mq, reportResults: ReportResults, mqType: String,
    msg: String, msgCount: Int, maxSendMsgBatchSize: Int) extends Runnable with StrictLogging {

  override def run() = {
    val mqSender = mq.createSender()
    try {
      var leftToSend = msgCount
      logger.info(s"Sending $leftToSend messages")
      while (leftToSend > 0) {
        val batchSize = math.min(leftToSend, Random.nextInt(maxSendMsgBatchSize) + 1)
        val fullMsg = msg + "_" + System.currentTimeMillis().toString
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