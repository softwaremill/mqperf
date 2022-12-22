package mqperf

import com.typesafe.scalalogging.StrictLogging
import io.prometheus.client.{Counter, Histogram}

import java.time.Clock
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class Receiver(config: Config, mq: Mq, clock: Clock) extends StrictLogging {

  private object LocalMetrics {
    private val testIdLabelValue = config.testId
    val messageCounter: Counter.Child = Metrics.Receiver.messageCounter.labels(testIdLabelValue)
    val messageLatencyHistogram: Histogram.Child = Metrics.Receiver.messageLatencyHistogram.labels(testIdLabelValue)
  }

  private val FinishWhenNoMessagesAfter = 60.seconds

  def run(): Future[Unit] = {
    val mqReceiver = mq.createReceiver(config)
    runIteration(mqReceiver).flatMap(_ => mqReceiver.close())
  }

  private def runIteration(mqReceiver: MqReceiver): Future[Unit] = {
    val batchSize = config.batchSizeReceive

    def receive(lastActivity: Long): Future[Unit] = {
      if (clock.millis() - lastActivity > FinishWhenNoMessagesAfter.toMillis) {
        logger.info(s"No messages received for ${FinishWhenNoMessagesAfter.toSeconds}s, stopping")
        Future.successful(())
      } else {
        mqReceiver
          .receive(batchSize)
          .flatMap { msgs =>
            val now = clock.millis()
            msgs.foreach { case (_, msg) =>
              val t = Timestamp.extract(msg)
              LocalMetrics.messageLatencyHistogram.observe((now - t).toDouble)
            }
            LocalMetrics.messageCounter.inc(msgs.size.toDouble)
            mqReceiver.ack(msgs.map(_._1)).map(_ => msgs.size)
          }
          .flatMap {
            case 0 => receive(lastActivity)
            case _ => receive(clock.millis())
          }
      }
    }

    val receiverConcurrency = config.receiverConcurrency
    Future
      .sequence(
        (1 to receiverConcurrency).map { _ => receive(clock.millis()) }
      )
      .map(_ => ())
  }
}
