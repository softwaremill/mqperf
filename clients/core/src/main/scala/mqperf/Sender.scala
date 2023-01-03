package mqperf

import cats.effect._
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import io.prometheus.client.{Counter, Histogram}

import java.time.Clock
import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, DurationLong}

class Sender(config: Config, mq: Mq, clock: Clock) extends StrictLogging {

  implicit val ioRuntime: IORuntime = cats.effect.unsafe.IORuntime.global
  private val messagesPool = RandomMessagesPool(config.msgSizeBytes)

  private object LocalMetrics {
    private val testIdLabelValue = config.testId
    val messageCounter: Counter.Child = Metrics.Sender.messageCounter.labels(testIdLabelValue)
    val messageLatencyHistogram: Histogram.Child = Metrics.Sender.messageLatencyHistogram.labels(testIdLabelValue)
  }

  def run(): Future[Unit] = {
    val mqSender = mq.createSender(config)
    logger.info("Starting sender...")

    val senderRun = runParallel(mqSender, config.senderConcurrency) >>
      IO(logger.info("Sending done, waiting for all messages to be flushed ...")) >>
      IO.sleep(3.seconds) >>
      IO.fromFuture(IO(mqSender.close()))

    senderRun
      .handleError(e => logger.error("Sender failed", e))
      .unsafeToFuture()
  }

  private def runParallel(mqSender: MqSender, senderConcurrency: Int): IO[Unit] = {
    val batchSize = config.batchSizeSend

    def sendRunner(permits: Long, testEnd: Long): IO[Unit] = {

      def iteration(sent: Long, permits: Long, startTimestamp: Long): IO[Unit] = {
        if (sent >= permits) {
          for {
            _ <- IO.sleep(Math.max(0, 1.second.toMillis - (clock.millis() - startTimestamp)).millis)
            result <- if (startTimestamp >= testEnd) IO.unit else iteration(0, permits, clock.millis())
          } yield result
        } else
          {
            for {
              sendStart <- IO(clock.millis())
              _ <- IO.fromFuture(IO(mqSender.send(nextMessagesBatch(batchSize))))
              _ <- IO {
                LocalMetrics.messageCounter.inc(batchSize.toDouble)
                LocalMetrics.messageLatencyHistogram.observe((clock.millis() - sendStart).toDouble)
              }
              result <- iteration(sent + batchSize, permits, startTimestamp)
            } yield result
          }
            .handleErrorWith { e =>
              IO(logger.error(s"Sender process failed. Tries to continue... ", e)) >>
                iteration(sent, permits, startTimestamp)
            }
      }

      for {
        startTimestamp <- IO(clock.millis())
        result <- iteration(0, permits, startTimestamp)
      } yield result
    }

    for {
      testEnd <- IO(clock.millis() + config.testLengthSeconds.seconds.toMillis)
      result <- (1 to senderConcurrency).map(_ => sendRunner(config.msgsPerProcessInSecond.toLong, testEnd)).toList.parSequence.void
    } yield result
  }

  private def nextMessagesBatch(batchSize: Int): Seq[String] = {
    (1 to batchSize)
      .map(_ => messagesPool.nextMessage())
      .map(Timestamp.add(_, clock))
  }
}
