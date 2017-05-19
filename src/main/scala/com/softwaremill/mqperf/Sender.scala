package com.softwaremill.mqperf

import com.softwaremill.mqperf.config.TestConfig
import com.softwaremill.mqperf.mq.Mq
import com.softwaremill.mqperf.util.PrometheusMetricServer
import com.typesafe.scalalogging.StrictLogging
import io.prometheus.client.{CollectorRegistry, Counter, Gauge, Histogram}

import scala.util.Random

object Sender {
  def main(args: Array[String]): Unit = {
    import PrometheusMetricServer._
    withMetricsServerSync(CollectorRegistry.defaultRegistry) {
      println("Starting sender...")
      val testConfig = TestConfig.load()

      val mq = Mq.instantiate(testConfig)
      val labelValues = defaultLabelValues(testConfig, TestConfig.hostId)
      val c = Counter.build("mqperf_sent_total", "number of sent messages").labelNames(DefaultLabelNames: _*).register()
      val g = Gauge.build("mqperf_sent_threads_done", "number of sent threads done").labelNames(DefaultLabelNames: _*).register()
      g.labels(labelValues: _*).set(0)
      val sr = new SenderRunnable(
        mq,
        testConfig.mqType, Msg.prefix(testConfig),
        testConfig.msgCountPerThread, testConfig.maxSendMsgBatchSize,
        c.labels(labelValues: _*), g.labels(labelValues: _*)
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
}

class SenderRunnable(mq: Mq, mqType: String,
  msgPrefix: String, msgCount: Int, maxSendMsgBatchSize: Int,
  sendCounter: Counter.Child, sendDone: Gauge.Child)
    extends Runnable with StrictLogging {

  override def run() = {
    val mqSender = mq.createSender()
    try {
      var leftToSend = msgCount
      logger.info(s"Sending $leftToSend messages")
      while (leftToSend > 0) {
        val batchSize = math.min(leftToSend, Random.nextInt(maxSendMsgBatchSize) + 1)
        val fullMsg = Msg.addTimestamp(msgPrefix)
        val batch = List.fill(batchSize)(fullMsg)
        logger.debug("Sending batch")
        mqSender.send(batch)
        leftToSend -= batchSize
        sendCounter.inc(batchSize)
      }
      sendDone.inc()
    }
    finally {
      mqSender.close()
    }
  }
}
