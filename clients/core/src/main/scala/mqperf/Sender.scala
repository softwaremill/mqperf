package mqperf

import com.typesafe.scalalogging.StrictLogging
import io.prometheus.client.{Counter, Histogram}

import java.time.Clock
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Future, blocking}
import scala.util.Failure

class Sender(config: Config, mq: Mq, clock: Clock) extends StrictLogging {

  private val messagesPool = RandomMessagesPool(config.msgSizeBytes)

  private object LocalMetrics {
    private val testIdLabelValue = config.testId
    val messageCounter: Counter.Child = Metrics.Sender.messageCounter.labels(testIdLabelValue)
    val messageLatencyHistogram: Histogram.Child = Metrics.Sender.messageLatencyHistogram.labels(testIdLabelValue)
  }

  def run(): Future[Unit] = {
    val mqSender = mq.createSender(config)

    Future {
      blocking {
        val end = clock.millis() + config.testLengthSeconds.seconds.toMillis
        while (clock.millis() < end) {
          val iterationStart = clock.millis()
          logger.info(s"Messages to send: ${config.msgsPerSecond} (concurrency=${config.senderConcurrency})")
          runIterations(mqSender)

          val iterationEnd = clock.millis()
          Thread.sleep(math.max(0, 1.second.toMillis - (iterationEnd - iterationStart)))
        }

        logger.info("Sending done, waiting for all messages to be flushed ...")
        Thread.sleep(3.second.toMillis)
      }
    }.flatMap(_ => mqSender.close())
  }

  private def runIterations(mqSender: MqSender): Future[Unit] = {
    val batchSize = config.batchSizeSend
    val sentMessages = new AtomicInteger(0)

    def send(): Future[Unit] = {
      if (sentMessages.get() >= config.msgsPerSecond) {
        Future.successful(())
      } else {
        val sendStart = clock.millis()
        sentMessages.addAndGet(batchSize)
        mqSender.send(nextMessagesBatch(batchSize)).flatMap { _ =>
          LocalMetrics.messageCounter.inc(batchSize.toDouble)
          LocalMetrics.messageLatencyHistogram.observe((clock.millis() - sendStart).toDouble)
          send()
        }
      }
    }

    val senderConcurrency = config.senderConcurrency
    Future
      .sequence(
        (1 to senderConcurrency).map { _ => send() }
      )
      .andThen{
        case Failure(ex) => logger.error("Sending iteration failure", ex)
      }
      .map(_ => ())
  }

  private def nextMessagesBatch(batchSize: Int): Seq[String] = {
    (1 to batchSize)
      .map(_ => messagesPool.nextMessage())
      .map(Timestamp.add(_, clock))
  }
}
