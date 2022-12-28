package mqperf

import com.typesafe.scalalogging.StrictLogging
import io.prometheus.client.{Counter, Histogram}

import java.time.Clock
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.Failure

class Sender(config: Config, mq: Mq, clock: Clock) extends StrictLogging {

  private val messagesPool = RandomMessagesPool(config.msgSizeBytes)
  private val loopEc = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())

  private object LocalMetrics {
    private val testIdLabelValue = config.testId
    val messageCounter: Counter.Child = Metrics.Sender.messageCounter.labels(testIdLabelValue)
    val messageLatencyHistogram: Histogram.Child = Metrics.Sender.messageLatencyHistogram.labels(testIdLabelValue)
  }

  def run(): Future[Unit] = {
    val mqSender = mq.createSender(config)
    val end = clock.millis() + config.testLengthSeconds.seconds.toMillis
    runIterations(mqSender, end)
      .map { _ =>
        logger.info("Sending done, waiting for all messages to be flushed ...")
        Thread.sleep(3.seconds.toMillis)
      }
      .flatMap(_ => mqSender.close())
  }

  private def runIterations(mqSender: MqSender, testEnd: Long): Future[Unit] = {
    val batchSize = config.batchSizeSend
    val sentMessages = new AtomicInteger(0)
    val permits = new AtomicInteger(0)

    def send(): Future[Unit] = {
      if (clock.millis() >= testEnd) {
        Future.successful(())
      } else if (sentMessages.get() < permits.get()) {
        val sendStart = clock.millis()
        sentMessages.addAndGet(batchSize)
        mqSender
          .send(nextMessagesBatch(batchSize))
          .map { _ =>
            LocalMetrics.messageCounter.inc(batchSize.toDouble)
            LocalMetrics.messageLatencyHistogram.observe((clock.millis() - sendStart).toDouble)
          }
          .flatMap(_ => send())
      } else {
        // do nothing, just wait for either: increasing of the permits or the test end
        send()
      }
    }

    // increases permits number every second
    Future {
      while (clock.millis() < testEnd) {
        val startTimestamp = clock.millis()
        permits.addAndGet(config.msgsPerSecond)
        logger.info(s"Messages to send: ${config.msgsPerSecond} (concurrency=${config.senderConcurrency})")
        val endTimestamp = clock.millis()
        Thread.sleep(Math.max(0, 1.second.toMillis - (endTimestamp - startTimestamp)))
      }
    }(loopEc)

    val senderConcurrency = config.senderConcurrency
    Future
      .sequence(
        (1 to senderConcurrency).map { _ => send() }
      )
      .andThen { case Failure(ex) =>
        logger.error("Sending iteration failure", ex)
      }
      .map(_ => ())
  }

  private def nextMessagesBatch(batchSize: Int): Seq[String] = {
    (1 to batchSize)
      .map(_ => messagesPool.nextMessage())
      .map(Timestamp.add(_, clock))
  }
}
