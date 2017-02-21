package com.softwaremill.mqperf

import java.util.concurrent.TimeUnit

import com.codahale.metrics.{MetricRegistry, Timer}
import com.softwaremill.mqperf.config.TestConfigOnS3
import com.softwaremill.mqperf.mq.Mq
import com.softwaremill.mqperf.util.{Clock, RealClock}
import com.typesafe.scalalogging.StrictLogging
import org.joda.time.DateTime

import scala.concurrent.duration._

object Receiver extends App {
  println("Starting receiver...")
  TestConfigOnS3.create(args).whenChanged { testConfig =>
    println(s"Starting test (receiver) with config: $testConfig")

    val mq = Mq.instantiate(testConfig)
    val report = new DynamoReportResults(testConfig.name)
    val rootTimestamp = new DateTime()
    val rr = new ReceiverRunnable(mq, report, testConfig.mqType, testConfig.receiveMsgBatchSize, new MetricRegistry, rootTimestamp)

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
    receiveMsgBatchSize: Int,
    metricRegistry: MetricRegistry,
    rootTimestamp: DateTime,
    clock: Clock = RealClock
) extends Runnable with StrictLogging {

  val timeout: FiniteDuration = 60.seconds
  val timeoutNanos: Long = timeout.toNanos

  override def run(): Unit = {
    import ReceiverMetrics._

    val threadId = Thread.currentThread().getId
    val msgTimer = metricRegistry.timer(s"$batchLatencyTimerPrefix-$threadId")
    // Calculates latency between sending message by the sender and receiving by the receiver
    val clusterLatencyTimer = metricRegistry.timer(s"$clusterLatencyTimerPrefix-$threadId")
    val meter = metricRegistry.meter(s"$batchThroughputMeter-$threadId")

    val mqReceiver = mq.createReceiver()

    try {
      var lastReceivedNano = clock.nanoTime()
      var waitingForFirstMessage = true

      while (waitingForFirstMessage || (clock.nanoTime() - lastReceivedNano) < timeoutNanos) {
        val received = doReceive(mqReceiver, msgTimer, clusterLatencyTimer)
        if (received > 0) {
          lastReceivedNano = clock.nanoTime()
          meter.mark()
          waitingForFirstMessage = false
        }
      }
      logger.info(s"Test finished, last message read $timeout ago")
      ReceiverMetrics(metricRegistry, rootTimestamp, threadId).foreach(reportResults.report)
    }
    finally {
      mqReceiver.close()
    }
  }

  private def doReceive(mqReceiver: mq.MqReceiver, msgTimer: Timer, clusterTimer: Timer): Int = {
    val before = clock.nanoTime()
    val msgs = mqReceiver.receive(receiveMsgBatchSize)
    if (msgs.nonEmpty) {
      val after = clock.nanoTime()
      val afterMs = clock.currentTimeMillis()
      msgs.foreach {
        case (_, msg) =>
          val msgTimestamp = mq.extractTimestamp(msg)
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
