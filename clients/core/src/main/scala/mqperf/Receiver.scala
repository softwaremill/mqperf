package mqperf

import com.typesafe.scalalogging.StrictLogging
import io.prometheus.client.{Counter, Histogram}
import mqperf.Server.testIdLabelName

import java.time.Clock
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class Receiver(config: Config, mq: Mq, clock: Clock) extends StrictLogging {

  private object Metrics {
    private val testIdLabelValue = config.testId

    val messageCounter: Counter.Child = Counter
      .build("mqperf_received_total", "number of received messages")
      .labelNames(testIdLabelName)
      .register()
      .labels(testIdLabelValue)
    val messageLatencyHistogram: Histogram.Child = Histogram
      .build("mqperf_latency_ms", "latency of received messages")
      .buckets(0, 50, 100, 150, 200, 250, 300, 350, 400, 450, 500, 600, 700, 800, 900, 1000, 1250, 1500, 1750, 2000, 2500, 3000, 3500, 4000,
        4500, 5000, 6000, 7000, 8000, 9000, 10000, 20000, 30000, 40000, 50000, 60000)
      .labelNames(testIdLabelName)
      .register()
      .labels(testIdLabelValue)
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
              Metrics.messageLatencyHistogram.observe(now - t)
            }
            Metrics.messageCounter.inc(msgs.size)

            mqReceiver.ack(msgs.map(_._1)).map(_ => msgs.size)
          }
          .flatMap {
            case 0 => run(mqReceiver, lastMessage)
            case _ => run(mqReceiver, Some(clock.millis()))
          }
    }
  }
}
