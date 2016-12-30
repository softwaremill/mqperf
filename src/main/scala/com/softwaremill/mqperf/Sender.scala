package com.softwaremill.mqperf

import java.util.concurrent.TimeUnit

import com.codahale.metrics.{Meter, MetricRegistry, Timer}
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
    val meter = metricRegistry.meter(s"sender-meter-$threadId")
    val mqSender = mq.createSender()
    val start = System.nanoTime()
    try {
      doSend(mqSender, msgTimer, meter, start)
      TestMetrics.send(metricRegistry).foreach(reportResults.report)
    }
    finally {
      mqSender.close()
    }
  }

  private def doSend(mqSender: mq.MqSender, msgTimer: Timer, meter: Meter, start: Long) {
    var leftToSend = msgCount
    logger.info(s"Sending $leftToSend messages")
    while (leftToSend > 0) {
      val batchSize = math.min(leftToSend, Random.nextInt(maxSendMsgBatchSize) + 1)
      val batch = List.fill(batchSize)(msg)
      val before = System.nanoTime()
      logger.debug("Sending batch")
      mqSender.send(batch)
      val after = System.nanoTime()
      meter.mark(batch.length)
      msgTimer.update(after - before, TimeUnit.NANOSECONDS)
      leftToSend -= batchSize
    }
  }
}