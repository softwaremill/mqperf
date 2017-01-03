package com.softwaremill.mqperf

import java.util.concurrent.TimeUnit
import com.codahale.metrics.{MetricRegistry, Timer}
import com.softwaremill.mqperf.config.TestConfigOnS3
import com.softwaremill.mqperf.mq.Mq
import com.typesafe.scalalogging.StrictLogging
import scala.concurrent.duration._

object Receiver extends App {
  println("Starting receiver...")
  TestConfigOnS3.create(args).whenChanged { testConfig =>
    println(s"Starting test (receiver) with config: $testConfig")

    val mq = Mq.instantiate(testConfig)
    val report = new ReportResults(testConfig.name)

    val rr = new ReceiverRunnable(mq, report, testConfig.mqType, testConfig.receiveMsgBatchSize)

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
    mqType: String,
    receiveMsgBatchSize: Int
) extends Runnable with StrictLogging {

  val timeout = 60.seconds
  val timeoutNanos = timeout.toNanos

  override def run(): Unit = {
    val metricRegistry = new MetricRegistry()
    val threadId = Thread.currentThread().getId
    val msgTimer = metricRegistry.timer(s"receiver-timer-$threadId")
    // Calculates latency between sending message by the sender and receiving by the receiver
    val clusterLatencyTimer = metricRegistry.timer(s"cluster-timer-$threadId")
    val meter = metricRegistry.meter(s"receiver-meter-$threadId")

    val mqReceiver = mq.createReceiver()

    try {
      val start = System.nanoTime()
      var lastReceivedNano = start

      while (System.nanoTime() - lastReceivedNano < timeoutNanos) {
        val received = doReceive(mqReceiver, msgTimer, clusterLatencyTimer)
        if (received > 0) {
          val nowNano = System.nanoTime()
          lastReceivedNano = nowNano
          meter.mark(received)
        }
      }
      logger.info(s"Test finished, last message read $timeout ago")
      TestMetrics.receive(metricRegistry).foreach(reportResults.report)
    }
    finally {
      mqReceiver.close()
    }
  }

  private def doReceive(mqReceiver: mq.MqReceiver, msgTimer: Timer, clusterTimer: Timer) = {
    val before = System.nanoTime()
    val msgs = mqReceiver.receive(receiveMsgBatchSize)
    if (msgs.nonEmpty) {
      val after = System.nanoTime()
      val afterMs = after / 1000000L
      msgs.foreach {
        case (_, msg) =>
          val msgTimestamp = msg.substring(msg.indexOf('_') + 1).toLong
          clusterTimer.update(afterMs - msgTimestamp, TimeUnit.MILLISECONDS)
      }
      msgTimer.update(after - before, TimeUnit.NANOSECONDS)
    }
    val ids = msgs.map(_._1)
    if (ids.nonEmpty) {
      mqReceiver.ack(ids)
    }
    ids.size
  }
}
