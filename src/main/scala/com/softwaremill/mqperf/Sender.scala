package com.softwaremill.mqperf

import com.softwaremill.mqperf.config.TestConfig
import com.softwaremill.mqperf.mq.Mq
import com.softwaremill.mqperf.util.PrometheusMetricServer.{DefaultLabelNames, defaultLabelValues}
import com.softwaremill.mqperf.util.{Clock, PrometheusMetricServer, RealClock}
import com.typesafe.scalalogging.StrictLogging
import io.prometheus.client.{CollectorRegistry, Counter, Gauge, Histogram}

import scala.util.Random

object Sender extends StrictLogging {
  def main(args: Array[String]): Unit = {
    Thread.setDefaultUncaughtExceptionHandler((t, e) => {
      println("Uncaught exception in thread: " + t)
      e.printStackTrace()
    })

    import PrometheusMetricServer._
    withMetricsServerSync(CollectorRegistry.defaultRegistry) {
      logger.info("Starting sender...")
      val testConfig = TestConfig.load()

      val mq: Mq = Mq.instantiate(testConfig)
      val (messagesCounter: Counter.Child, threadsDoneCounter: Gauge.Child, sendLatency: Histogram.Child) = createMetrics(testConfig)
      val messagesPool: RandomMessagesPool = RandomMessagesPool(testConfig.msgSize, RandomMessagesPool.DefaultMessagesPoolSize)

      val sr = new SenderRunnable(
        mq,
        testConfig.mqType,
        testConfig.msgCountPerThread,
        testConfig.maxSendMsgBatchSize,
        messagesCounter,
        threadsDoneCounter,
        sendLatency,
        messagesPool
      )

      val threads = (1 to testConfig.senderThreads).map { _ =>
        val t = new Thread(sr)
        t.start()
        t
      }

      threads.foreach(_.join())

      logger.info("Closing mq")
      mq.close()
    }
  }

  private def createMetrics(testConfig: TestConfig): (Counter.Child, Gauge.Child, Histogram.Child) = {
    val labelValues = defaultLabelValues(testConfig, TestConfig.hostId)

    val messagesCounter = Counter.build("mqperf_sent_total", "number of sent messages").labelNames(DefaultLabelNames: _*).register()

    val threadsDoneCounter = Gauge
      .build("mqperf_sent_threads_done", "number of send threads done")
      .labelNames(DefaultLabelNames: _*)
      .register()
    threadsDoneCounter.labels(labelValues: _*).set(0)

    val messageLatencyHistogram = Histogram
      .build("mqperf_send_latency_ms", "latency of sent messages")
      .buckets(0, 50, 100, 150, 200, 250, 300, 350, 400, 450, 500, 600, 700, 800, 900, 1000, 1250, 1500, 1750, 2000,
        2500, 3000, 3500, 4000, 4500, 5000)
      .labelNames(DefaultLabelNames: _*)
      .register()

    (messagesCounter.labels(labelValues: _*),
      threadsDoneCounter.labels(labelValues: _*),
      messageLatencyHistogram.labels(labelValues: _*))
  }
}

class SenderRunnable(
                      mq: Mq,
                      mqType: String,
                      msgCount: Int,
                      maxSendMsgBatchSize: Int,
                      sendCounter: Counter.Child,
                      sendDone: Gauge.Child,
                      sendLatency: Histogram.Child,
                      messagesPool: RandomMessagesPool,
                      clock: Clock = RealClock
                    ) extends Runnable
  with StrictLogging {

  override def run(): Unit = {
    val mqSender = mq.createSender()

    try {
      var leftToSend = msgCount
      logger.info(s"Sending $leftToSend messages to the $mqType queue")

      while (leftToSend > 0) {
        val batchSize = math.min(leftToSend, Random.nextInt(maxSendMsgBatchSize) + 1)
        val batch = createMessagesBatch(batchSize)
        logger.debug("Sending batch")

        val start = clock.currentTimeMillis()
        mqSender.send(batch)
        sendLatency.observe(clock.currentTimeMillis() - start)

        leftToSend -= batchSize
        sendCounter.inc(batchSize)
      }

      sendDone.inc()
    } finally {
      mqSender.close()
      logger.info(s"Sending done.")
    }
  }

  private def createMessagesBatch(batchSize: Integer): List[String] = {
    (1 to batchSize)
      .map(_ => messagesPool.nextMessage())
      .map(Msg.addTimestamp)
      .toList
  }
}
