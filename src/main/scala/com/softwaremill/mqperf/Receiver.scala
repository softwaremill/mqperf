package com.softwaremill.mqperf

import com.softwaremill.mqperf.config.TestConfigOnS3
import com.softwaremill.mqperf.mq.Mq

object Receiver extends App {
  println("Starting receiver...")
  TestConfigOnS3.create(args).whenChanged { testConfig =>
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

    mq.close()
  }
}

class ReceiverRunnable(
  mq: Mq,
  reportResults: ReportResults,
  receiveMsgBatchSize: Int) extends Runnable {

  override def run() = {
    val mqReceiver = mq.createReceiver()

    val start = System.currentTimeMillis()
    var lastReceived = System.currentTimeMillis()
    var totalReceived = 0

    while ((System.currentTimeMillis() - lastReceived) < 60*1000L) {
      val received = doReceive(mqReceiver)

      if (received > 0) {
        lastReceived = System.currentTimeMillis()
      }

      totalReceived += received
    }

    reportResults.reportReceivingComplete(start, lastReceived, totalReceived)
    mqReceiver.close()
  }

  private def doReceive(mqReceiver: mq.MqReceiver) = {
    val msgs = mqReceiver.receive(receiveMsgBatchSize)
    val ids = msgs.map(_._1)
    if (ids.size > 0) {
      mqReceiver.ack(ids)
    }

    ids.size
  }
}