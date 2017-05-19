package com.softwaremill.mqperf

import com.softwaremill.mqperf.config.TestConfig
import com.softwaremill.mqperf.mq.Mq
import com.softwaremill.mqperf.util.{Clock, PrometheusMetricServer, RealClock}
import com.typesafe.scalalogging.StrictLogging
import io.prometheus.client.{CollectorRegistry, Counter, Gauge, Histogram}
import org.joda.time.DateTime

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object Receiver extends App {
  val metricsExporter = PrometheusMetricServer.start(CollectorRegistry.defaultRegistry, "0.0.0.0", 9193)
  metricsExporter.onFailure { case _ => System.exit(-1) }

  println("Starting receiver...")
  val testConfig = TestConfig.load()
  val hostId = sys.env.getOrElse("HOST_ID", throw new IllegalStateException("No HOST_ID defined in the environment!"))

  val mq = Mq.instantiate(testConfig)
  val rootTimestamp = new DateTime()

  val labelNames = List("test", "run", "host")
  val labelValues = List(testConfig.name, testConfig.runId, hostId)

  val c = Counter.build("mqperf_received_total", "number of received messages").labelNames(labelNames: _*).register()
  val h = Histogram.build("mqperf_latency_ms", "latency of received messages")
    .buckets(0, 50, 100, 150, 200, 250, 300, 350, 400, 450, 500, 600, 700, 800, 900,
      1000, 1250, 1500, 1750,
      2000, 2500, 3000, 3500, 4000, 4500,
      5000, 6000, 7000, 8000, 9000, 10000)
    .labelNames(labelNames: _*).register()
  val g = Gauge.build("mqperf_receive_threads_done", "number of receive threads done").labelNames(labelNames: _*).register()
  g.labels(labelValues: _*).set(0)

  val threads = (1 to testConfig.receiverThreads).map { _ =>
    val t = new Thread(new ReceiverRunnable(mq, testConfig.mqType, testConfig.receiveMsgBatchSize, rootTimestamp,
      c.labels(labelValues: _*),
      h.labels(labelValues: _*),
      g.labels(labelValues: _*)))
    t.start()
    t
  }

  threads.foreach(_.join())

  mq.close()

  metricsExporter.foreach(_())
}

class ReceiverRunnable(
    mq: Mq,
    mqType: String,
    receiveMsgBatchSize: Int,
    rootTimestamp: DateTime,
    receiveCounter: Counter.Child,
    receiveLatency: Histogram.Child,
    receiveDone: Gauge.Child,
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
          receiveCounter.inc(received)
          waitingForFirstMessage = false
        }
      }
      val tookMs = lastReceivedMs - rootTimestamp.getMillis
      val msgss = totalReceived / (tookMs / 1000)
      logger.info(s"Test finished, last message read $timeout ago, received a total of $totalReceived over ${tookMs}ms, that is $msgss msgs/s")
      receiveDone.inc()
    }
    finally {
      mqReceiver.close()
    }
  }

  private def doReceive(mqReceiver: mq.MqReceiver): Int = {
    val msgs = mqReceiver.receive(receiveMsgBatchSize)
    if (msgs.nonEmpty) {
      val afterMs = clock.currentTimeMillis()
      msgs.foreach {
        case (_, msg) =>
          val msgTimestamp = Msg.extractTimestamp(msg)
          receiveLatency.observe(afterMs - msgTimestamp)
      }
    }
    val ids = msgs.map(_._1)
    if (ids.nonEmpty) {
      mqReceiver.ack(ids)
    }
    ids.size
  }
}