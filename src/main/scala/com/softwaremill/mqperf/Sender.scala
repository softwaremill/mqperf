package com.softwaremill.mqperf

import java.util.concurrent.TimeUnit
import com.codahale.metrics.{Histogram, MetricRegistry, Timer}
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

  val metricRegistry = new MetricRegistry()
  val msgTimer = metricRegistry.timer(s"$mqType-sender-timer")

  override def run() = {
    val metricRegistry = new MetricRegistry()
    val threadId = Thread.currentThread().getId
    val msgTimer = metricRegistry.timer(s"sender-timer-$threadId")
    val histogram = metricRegistry.histogram(s"sender-histogram-$threadId")
    val mqSender = mq.createSender()
    try {
      doSend(mqSender, msgTimer, histogram)
      TestMetrics.send(metricRegistry).foreach(reportResults.report)
    }
    finally {
      mqSender.close()
    }
  }

  private def doSend(mqSender: mq.MqSender, msgTimer: Timer, histogram: Histogram) {
    var leftToSend = msgCount
    logger.info(s"Sending $leftToSend messages")
    val start = System.nanoTime()
    while (leftToSend > 0) {
      val batchSize = math.min(leftToSend, Random.nextInt(maxSendMsgBatchSize) + 1)
      val batch = List.fill(batchSize)(msg)
      val before = System.nanoTime()
      mqSender.send(batch)
      val after = System.nanoTime()
      val nowSeconds = (after - start) / 1000000000L
      batch.foreach(_ => histogram.update(nowSeconds))
      msgTimer.update(after - before, TimeUnit.NANOSECONDS)
      leftToSend -= batchSize
    }
  }
}