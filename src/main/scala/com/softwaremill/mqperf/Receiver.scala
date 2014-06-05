package com.softwaremill.mqperf

import com.softwaremill.mqperf.config.TestConfigOnS3
import com.softwaremill.mqperf.mq.Mq

object Receiver extends App {
  new TestConfigOnS3().whenChanged { testConfig =>
    println(s"Starting test (receiver) with config: $testConfig")

    val mq = Mq.instantiate(testConfig)
    val report = new ReportResults(testConfig.name)
    val rr = new ReceiverRunnable(
      mq, report,
      testConfig.receiveMsgBatchSize
    )

    val threads = (1 to testConfig.receiverThreads).map { _ =>
      val t = new Thread(rr)
      t.start()
      t
    }

    threads.foreach(_.join())
  }
}

class ReceiverRunnable(
  mq: Mq,
  reportResults: ReportResults,
  receiveMsgBatchSize: Int) extends Runnable {

  override def run() = {
    val start = System.currentTimeMillis()
    var lastReceived = System.currentTimeMillis()
    var totalReceived = 0

    while ((System.currentTimeMillis() - lastReceived) < 60*1000L) {
      val received = doReceive()

      if (received > 0) {
        lastReceived = System.currentTimeMillis()
      }

      totalReceived += received
    }

    reportResults.reportReceivingComplete(start, lastReceived, totalReceived)
  }

  private def doReceive() = {
    val msgs = mq.receive(receiveMsgBatchSize)
    val ids = msgs.map(_._1)
    if (ids.size > 0) {
      mq.ack(ids)
    }

    ids.size
  }
}