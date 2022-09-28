package mqperf

import com.typesafe.scalalogging.StrictLogging
import io.prometheus.client.{Counter, Histogram}
import mqperf.Server.testIdLabelName

import java.time.Clock
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, blocking}
import scala.util.{Failure, Success}

class Sender(config: Config, mq: Mq, clock: Clock) extends StrictLogging {

  private val messagesPool = RandomMessagesPool(config.msgSize)

  private object Metrics {
    private val testIdLabelValue = config.testId

    val messagesCounter: Counter.Child =
      Counter.build("mqperf_sent_total", "number of sent messages").labelNames(testIdLabelName).register().labels(testIdLabelValue)

    val messageLatencyHistogram: Histogram.Child = Histogram
      .build("mqperf_send_latency_ms", "latency of sent messages")
      .buckets(0, 50, 100, 150, 200, 250, 300, 350, 400, 450, 500, 600, 700, 800, 900, 1000, 1250, 1500, 1750, 2000, 2500, 3000, 3500, 4000,
        4500, 5000)
      .labelNames(testIdLabelName)
      .register()
      .labels(testIdLabelValue)
  }

  def run(): Future[Unit] = {
    val mqSender = mq.createSender(config)

    Future {
      blocking {
        val start = clock.millis()
        val end = start + config.testLength.toMillis

        val permits = new AtomicInteger(config.maxSendInFlight)
        val batchSize = config.batchSizeSend

        while (clock.millis() < end) {
          val iterationStart = clock.millis()
          // we send at most `config.msgsPerSecond` messages, but no more than the number of available permits
          val msgsToSend = math.min(config.msgsPerSecond, permits.getAndUpdate(p => math.max(0, p - config.msgsPerSecond)))
          logger.info(s"Messages to send: $msgsToSend")
          val batches = msgsToSend / batchSize
          (1 to batches).foreach { _ =>
            val sendStart = clock.millis()
            mqSender.send(nextMessagesBatch()).onComplete { result =>
              permits.addAndGet(batchSize)
              Metrics.messagesCounter.inc(batchSize)
              Metrics.messageLatencyHistogram.observe(clock.millis() - sendStart)
              result match {
                case Failure(t) => logger.error("Exception when sending a batch of messages", t)
                case Success(_) =>
              }
            }
          }

          val iterationEnd = clock.millis()
          // a whole iteration should take at least a second
          Thread.sleep(math.max(0, 1000L - (iterationEnd - iterationStart)))
        }

        logger.info("Sending done, waiting for all messages to be flushed ...")
        Thread.sleep(3000L)
      }
    }.flatMap(_ => mqSender.close())
  }

  private def nextMessagesBatch(): Seq[String] = {
    (1 to config.batchSizeSend)
      .map(_ => messagesPool.nextMessage())
      .map(Timestamp.add(_, clock))
  }
}
