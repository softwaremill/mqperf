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
    run(mqReceiver, None).flatMap(_ => mqReceiver.close())
  }

  private def run(mqReceiver: MqReceiver, lastMessage: Option[Long]): Future[Unit] = {
    lastMessage match {
      case Some(last) if clock.millis() - last > FinishWhenNoMessagesAfter.toMillis =>
        logger.info(s"No messages received for ${FinishWhenNoMessagesAfter.toSeconds}s, stopping")
        Future.successful(())
      case _ =>
        mqReceiver
          .receive(config.batchSizeReceive)
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
            case 0 => run(mqReceiver, lastMessage)
            case _ => run(mqReceiver, Some(clock.millis()))
          }
    }
  }
}
