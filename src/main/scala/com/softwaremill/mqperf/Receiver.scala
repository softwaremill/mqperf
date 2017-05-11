package com.softwaremill.mqperf

import com.softwaremill.mqperf.config.TestConfig
import com.softwaremill.mqperf.mq.Mq
import com.softwaremill.mqperf.util.{Clock, RealClock}
import com.timgroup.statsd.{NonBlockingStatsDClient, StatsDClient}
import com.typesafe.scalalogging.StrictLogging
import org.joda.time.DateTime

import scala.concurrent.duration._

object Receiver extends App {
  println("Starting receiver...")
  val testConfig = TestConfig.load()

  val mq = Mq.instantiate(testConfig)
  val rootTimestamp = new DateTime()

  val statsd = new NonBlockingStatsDClient(
    "",
    "localhost",
    8125,
    s"test:${testConfig.name}",
    s"run:${testConfig.runId}"
  )

  val threads = (1 to testConfig.receiverThreads).map { _ =>
    val t = new Thread(new ReceiverRunnable(mq, testConfig.mqType, testConfig.receiveMsgBatchSize,
      rootTimestamp, statsd))
    t.start()
    t
  }

  threads.foreach(_.join())

  mq.close()
}

class ReceiverRunnable(
    mq: Mq,
    mqType: String,
    receiveMsgBatchSize: Int,
    rootTimestamp: DateTime,
    statsd: StatsDClient,
    clock: Clock = RealClock
) extends Runnable with StrictLogging {

  val timeout: FiniteDuration = 60.seconds
  val timeoutMs: Long = timeout.toMillis

  override def run(): Unit = {
    val mqReceiver = mq.createReceiver()

    try {
      var lastReceivedMs = clock.currentTimeMillis()
      var waitingForFirstMessage = true
      var totalReceived = 0

      while (waitingForFirstMessage || (clock.currentTimeMillis() - lastReceivedMs) < timeoutMs) {
        val received = doReceive(mqReceiver)
        if (received > 0) {
          totalReceived += received
          lastReceivedMs = clock.currentTimeMillis()
          statsd.count("mqperf_receive", received)
          waitingForFirstMessage = false
        }
      }
      val tookMs = lastReceivedMs - rootTimestamp.getMillis
      logger.info(s"Test finished, last message read $timeout ago, received a total of $totalReceived over ${tookMs}ms, that is ${totalReceived / (tookMs / 1000)} msgs/s")
    }
    finally {
      mqReceiver.close()
    }
  }

  private def doReceive(mqReceiver: mq.MqReceiver): Int = {
    val before = clock.nanoTime()
    val msgs = mqReceiver.receive(receiveMsgBatchSize)
    if (msgs.nonEmpty) {
      val after = clock.nanoTime()
      val afterMs = clock.currentTimeMillis()
      msgs.foreach {
        case (_, msg) =>
          val msgTimestamp = Msg.extractTimestamp(msg)
          statsd.recordExecutionTime("mqperf_cluster_latency", afterMs - msgTimestamp)
      }
      statsd.recordExecutionTime("mqperf_receive_batch", after - before)
    }
    val ids = msgs.map(_._1)
    if (ids.nonEmpty) {
      mqReceiver.ack(ids)
    }
    ids.size
  }
}
