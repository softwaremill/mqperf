package com.softwaremill.mqperf

import java.util.concurrent.TimeUnit

import com.codahale.metrics.{MetricRegistry, Timer}
import com.softwaremill.mqperf.config.TestConfig
import com.softwaremill.mqperf.mq.Mq
import com.softwaremill.mqperf.util.{Clock, RealClock}
import com.typesafe.scalalogging.StrictLogging
import org.joda.time.DateTime

import scala.concurrent.duration._

object Receiver extends App {
  println("Starting receiver...")
  val testConfig = TestConfig.load()

  val mq = Mq.instantiate(testConfig)
  val report = new DynamoReportResults(testConfig.nodeId, testConfig.name)
  val rootTimestamp = new DateTime()
  val metricRegistry = new MetricRegistry

  val threads = (1 to testConfig.receiverThreads).map { threadId =>
    val t = new Thread(new ReceiverRunnable(mq, report, testConfig.mqType, testConfig.receiveMsgBatchSize,
      metricRegistry, rootTimestamp, threadId))
    t.start()
    t
  }

  threads.foreach(_.join())

  mq.close()
}

class ReceiverRunnable(
    mq: Mq,
    reportResults: ReportResults,
    mqType: String,
    receiveMsgBatchSize: Int,
    metricRegistry: MetricRegistry,
    rootTimestamp: DateTime,
    threadId: Int,
    clock: Clock = RealClock
) extends Runnable with StrictLogging {

  val timeout: FiniteDuration = 60.seconds
  val timeoutNanos: Long = timeout.toNanos

  override def run(): Unit = {
    val receiveBatchTimer = metricRegistry.timer(s"receive-batch-timer-$threadId")
    // Calculates latency between sending message by the sender and receiving by the receiver
    val clusterLatencyTimer = metricRegistry.timer(s"cluster-latency-timer-$threadId")
    val receiveThroughputMeter = metricRegistry.meter(s"receiver-throughput-meter-$threadId")

    val mqReceiver = mq.createReceiver()

    try {
      var lastReceivedNano = clock.nanoTime()
      var waitingForFirstMessage = true

      while (waitingForFirstMessage || (clock.nanoTime() - lastReceivedNano) < timeoutNanos) {
        val received = doReceive(mqReceiver, receiveBatchTimer, clusterLatencyTimer)
        if (received > 0) {
          lastReceivedNano = clock.nanoTime()
          receiveThroughputMeter.mark(received)
          waitingForFirstMessage = false
        }
      }
      logger.info(s"Test finished, last message read $timeout ago")
      reportResults.report(ReceiverMetrics(rootTimestamp, threadId, receiveThroughputMeter, receiveBatchTimer,
        clusterLatencyTimer, metricRegistry))
    }
    finally {
      mqReceiver.close()
    }
  }

  private def doReceive(mqReceiver: mq.MqReceiver, receiveBatchTimer: Timer, clusterLatencyTimer: Timer): Int = {
    val before = clock.nanoTime()
    val msgs = mqReceiver.receive(receiveMsgBatchSize)
    if (msgs.nonEmpty) {
      val after = clock.nanoTime()
      val afterMs = clock.currentTimeMillis()
      msgs.foreach {
        case (_, msg) =>
          val msgTimestamp = Msg.extractTimestamp(msg)
          clusterLatencyTimer.update(afterMs - msgTimestamp, TimeUnit.MILLISECONDS)
      }
      receiveBatchTimer.update(after - before, TimeUnit.NANOSECONDS)
    }
    val ids = msgs.map(_._1)
    if (ids.nonEmpty) {
      mqReceiver.ack(ids)
    }
    ids.size
  }
}
