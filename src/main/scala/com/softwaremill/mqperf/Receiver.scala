package com.softwaremill.mqperf

import java.util.concurrent.TimeUnit

import scala.concurrent.duration._
import com.codahale.metrics.MetricRegistry
import com.softwaremill.mqperf.config.TestConfigOnS3
import com.softwaremill.mqperf.mq.Mq
import com.typesafe.scalalogging.{LazyLogging, StrictLogging}

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
    receiveMsgBatchSize: Int
) extends Runnable with StrictLogging {

  val timeout = 16.seconds
  val timeoutNanos = timeout.toNanos

  val metricRegistry = new MetricRegistry()
  val msgTimer = metricRegistry.timer("kafka-receiver-messages-timer")
  val histogram = metricRegistry.histogram("kafka-receiver-messages-histogram")

  override def run(): Unit = {
    val mqReceiver = mq.createReceiver()
    try {
      val start = System.nanoTime()
      var lastReceivedNano = start

      while (System.nanoTime() - lastReceivedNano < timeoutNanos) {
        val received = doReceive(mqReceiver)
        if (received > 0) {
          val nowNano = System.nanoTime()
          val nowSeconds = nowNano / 1000000000L
          lastReceivedNano = nowNano
          (0 to received).foreach(_ => histogram.update(nowSeconds))
        }
      }
      logger.info(s"Test finished, last message read $timeout ago")
      TestMetrics.receive(metricRegistry).foreach(reportResults.report)
    }
    finally {
      mqReceiver.close()
    }
  }

  private def doReceive(mqReceiver: mq.MqReceiver) = {
    val before = System.nanoTime()
    val msgs = mqReceiver.receive(receiveMsgBatchSize)
    if (msgs.nonEmpty) {
      val after = System.nanoTime()
      msgTimer.update(after - before, TimeUnit.NANOSECONDS)
    }
    val ids = msgs.map(_._1)
    if (ids.nonEmpty) {
      mqReceiver.ack(ids)
    }
    ids.size
  }
}
