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

  val threads = (1 to testConfig.receiverThreads).map { threadId =>
    val t = new Thread(new ReceiverRunnable(mq, testConfig.mqType, testConfig.receiveMsgBatchSize,
      rootTimestamp, threadId, statsd))
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
    threadId: Int,
    statsd: StatsDClient,
    clock: Clock = RealClock
) extends Runnable with StrictLogging {

  val timeout: FiniteDuration = 60.seconds
  val timeoutNanos: Long = timeout.toNanos

  override def run(): Unit = {
    val mqReceiver = mq.createReceiver()

    try {
      var lastReceivedNano = clock.nanoTime()
      var waitingForFirstMessage = true

      while (waitingForFirstMessage || (clock.nanoTime() - lastReceivedNano) < timeoutNanos) {
        val received = doReceive(mqReceiver)
        if (received > 0) {
          lastReceivedNano = clock.nanoTime()
          statsd.count("mqperf_receive", received)
          waitingForFirstMessage = false
        }
      }
      logger.info(s"Test finished, last message read $timeout ago")
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
          statsd.histogram("mqperf_cluster_latency", afterMs - msgTimestamp)
      }
      statsd.histogram("mqperf_receive_batch", after - before)
    }
    val ids = msgs.map(_._1)
    if (ids.nonEmpty) {
      mqReceiver.ack(ids)
    }
    ids.size
  }
}
