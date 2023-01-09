package mqperf

import cats.effect.IO
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
    val sendersNumber = config.sendersNumber
    logger.info(s"Starting $sendersNumber senders...")
    val mqSenderFactory = mq.createSenderFactory(config)

    val senderProgram = (id: Int) =>
      for {
        mqSender <- IO(mqSenderFactory.createSender())
        _ <- runMqSender(id, mqSender, config.senderConcurrency)
        _ <- IO(logger.info(s"Sender[$id] done, waiting for all messages to be flushed ..."))
        closed <- IO.fromFuture(IO(mqSender.close()))
      } yield closed

    List
      .range(0, config.sendersNumber)
      .map { id =>
        senderProgram(id).handleError(e => logger.error(s"Sender[$id] failed", e))
      }
      .parSequence
      .flatMap { _ =>
        IO(mqSenderFactory.close())
      }
      .void
      .unsafeToFuture()
  }

  private def runMqSender(senderId: Int, mqSender: MqSender, senderConcurrency: Int): IO[Unit] = {
    for {
      testEnd <- IO(clock.millis() + config.testLengthSeconds.seconds.toMillis)
      result <- List
        .range(0, senderConcurrency)
        .map(_ => startConcurrentRunner(senderId, mqSender, config.msgsPerProcessInSecond.toLong, testEnd))
        .parSequence
        .void
    } yield result
  }

  private def startConcurrentRunner(senderId: Int, mqSender: MqSender, permits: Long, testEnd: Long): IO[Unit] = {
    val batchSize = config.batchSizeSend

    def sleepAndSend(startTimestamp: Long): IO[Unit] = {
      for {
        _ <- IO.sleep(Math.max(0, 1.second.toMillis - (clock.millis() - startTimestamp)).millis)
        result <- concurrentRunner(0, permits, clock.millis())
      } yield result
    }

    def send(sent: Long, permits: Long, startTimestamp: Long): IO[Unit] = {
      val iteration =
        for {
          sendStart <- IO(clock.millis())
          _ <- IO.fromFuture(IO(mqSender.send(nextMessagesBatch(batchSize))))
          _ <- IO {
            LocalMetrics.messageCounter.inc(batchSize.toDouble)
            LocalMetrics.messageLatencyHistogram.observe((clock.millis() - sendStart).toDouble)
          }
          result <- concurrentRunner(sent + batchSize, permits, startTimestamp)
        } yield result

      iteration
        .handleErrorWith { e =>
          IO(logger.error(s"Sender[$senderId] process failed. Tries to continue... ", e)) >>
            concurrentRunner(sent, permits, startTimestamp)
        }
    }

    def concurrentRunner(sent: Long, permits: Long, startTimestamp: Long): IO[Unit] = {
      if (clock.millis() >= testEnd) {
        IO.unit
      } else if (sent >= permits) {
        IO(logger.info(s"Sender[$senderId] sent $sent messages")) >>
          sleepAndSend(startTimestamp)
      } else {
        send(sent, permits, startTimestamp)
      }
    }

    concurrentRunner(0, permits, clock.millis())
  }

  private def nextMessagesBatch(batchSize: Int): Seq[String] = {
    (1 to batchSize)
      .map(_ => messagesPool.nextMessage())
      .map(Timestamp.add(_, clock))
  }
}
