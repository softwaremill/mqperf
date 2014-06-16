package com.softwaremill.mqperf

import com.softwaremill.mqperf.mq.Mq
import scala.util.Random
import com.softwaremill.mqperf.config.TestConfigOnS3

object Sender extends App {
  new TestConfigOnS3().whenChanged { testConfig =>
    println(s"Starting test (sender) with config: $testConfig")

    val mq = Mq.instantiate(testConfig)
    val report = new ReportResults(testConfig.name)
    val sr = new SenderRunnable(
      mq, report,
      "0" * testConfig.msgSize,
      testConfig.msgCountPerThread, testConfig.maxSendMsgBatchSize
    )

    val threads = (1 to testConfig.senderThreads).map { _ =>
      val t = new Thread(sr)
      t.start()
      t
    }

    threads.foreach(_.join())
  }
}

class SenderRunnable(mq: Mq, reportResults: ReportResults,
  msg: String, msgCount: Int, maxSendMsgBatchSize: Int) extends Runnable {

  private val mqSender = mq.createSender()

  override def run() = {
    val start = System.currentTimeMillis()
    doSend()
    val end = System.currentTimeMillis()
    reportResults.reportSendingComplete(start, end, msgCount)
    mqSender.close()
  }

  private def doSend() {
    var leftToSend = msgCount
    while (leftToSend > 0) {
      val batchSize = math.min(leftToSend, Random.nextInt(maxSendMsgBatchSize) + 1)
      mqSender.send(List.fill(batchSize)(msg))
      leftToSend -= batchSize
    }
  }
}