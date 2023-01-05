package mqperf

import com.typesafe.scalalogging.StrictLogging
import io.prometheus.client.{Counter, Histogram}

import java.time.Clock
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

class Receiver(config: Config, mq: Mq, clock: Clock) extends StrictLogging {

  private object LocalMetrics {
    private val testIdLabelValue = config.testId
    val messageCounter: Counter.Child = Metrics.Receiver.messageCounter.labels(testIdLabelValue)
    val messageLatencyHistogram: Histogram.Child = Metrics.Receiver.messageLatencyHistogram.labels(testIdLabelValue)
  }

  private val FinishWhenNoMessagesAfter = 60.seconds

  def run(): Future[Unit] = {
    Future.sequence {
      (1 to config.receiversNumbers).map(_ => {
        mq.receiverFactory(config).createReceiver()
      }) // prepare n receivers (n = receiversNumbers)
        .map(receiver => { // run iteration for each receiver on a separate future and close the connection afterwards
        runIterations(receiver)
          .flatMap(_ => receiver.close())
      })
    }.map(_ => ())
  }

  private def runIterations(mqReceiver: MqReceiver): Future[Unit] = {
    val batchSize = config.batchSizeReceive

    def receive(lastActivity: Long): Future[Unit] = {
      if (clock.millis() - lastActivity > FinishWhenNoMessagesAfter.toMillis) {
        Future.successful(())
      } else {
        mqReceiver
          .receive(batchSize)
          .map { msgs =>
            val now = clock.millis()
            msgs.foreach { case (_, msg) =>
              val t = Timestamp.extract(msg)
              LocalMetrics.messageLatencyHistogram.observe((now - t).toDouble)
            }
            LocalMetrics.messageCounter.inc(msgs.size.toDouble)
            mqReceiver
              .ack(msgs.map(_._1))
              .andThen { case Failure(ex) =>
                logger.error("Ack of received messages failure", ex)
              }
            msgs.size
          }
          .flatMap {
            case 0 => receive(lastActivity)
            case _ => receive(clock.millis())
          }
          .flatMap(lastActivitiesSeq => {
            receive(lastActivitiesSeq.max)
          })
      }
    }

    val receiverConcurrency = config.receiverConcurrency
    Future
      .sequence(
        (1 to receiverConcurrency).map { _ => receive(clock.millis()) }
      )
      .andThen {
        case Success(_)  => logger.info(s"No messages received for ${FinishWhenNoMessagesAfter.toSeconds}s, stopping")
        case Failure(ex) => logger.error("Receiving iteration failure", ex)
      }
      .map(_ => ())
  }
}
