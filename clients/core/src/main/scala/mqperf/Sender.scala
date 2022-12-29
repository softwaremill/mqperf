package mqperf

import cats.effect._
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import io.prometheus.client.{Counter, Histogram}

import java.time.Clock
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class Sender(config: Config, mq: Mq, clock: Clock) extends StrictLogging {

  implicit val ioRuntime: IORuntime = cats.effect.unsafe.IORuntime.global
  private val messagesPool = RandomMessagesPool(config.msgSizeBytes)

  private object LocalMetrics {
    private val testIdLabelValue = config.testId
    val messageCounter: Counter.Child = Metrics.Sender.messageCounter.labels(testIdLabelValue)
    val messageLatencyHistogram: Histogram.Child = Metrics.Sender.messageLatencyHistogram.labels(testIdLabelValue)
  }

  /*
    sent - number of messages already sent
    permits - number of messages which sender is allowed to send
    currentIteration - number of iteration, 1 iteration lasts 1 second
    nextIterationHook - allows sender to wait for next iteration without thread-blocking. If Deferred will evaluate to
      false sender will start nextIteration, otherwise it will finish sending.
   */
  case class State(sent: Long, permits: Long, currentIteration: Int, nextIterationHook: Deferred[IO, Boolean])

  def run(): Future[Unit] = {
    val mqSender = mq.createSender(config)

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

    def sendRunner(state: Ref[IO, State], iterationNo: Int = 0): IO[Unit] = {
      def iteration(state: Ref[IO, State]): IO[Unit] = {
        state.get.flatMap(s =>
          if (s.sent >= s.permits) {
            IO.unit
          }
          else {
            for {
              sendStart <- IO(clock.millis())
              _ <- IO.fromFuture(IO(mqSender.send(nextMessagesBatch(batchSize))))
              _ <- state.update { s => s.copy(sent = s.sent + batchSize) }
              _ <- IO {
                LocalMetrics.messageCounter.inc(batchSize.toDouble)
                LocalMetrics.messageLatencyHistogram.observe((clock.millis() - sendStart).toDouble)
              }
              result <- iteration(state)
            } yield result
          }
        )
        .handleErrorWith{ e =>
          IO(logger.error(s"Sender iteration[$iterationNo] failed ", e)) >>
            iteration(state)
        }
      }

      state.get.flatMap(s => {
        if(iterationNo < s.currentIteration) {
          iteration(state) >> sendRunner(state, iterationNo + 1)
        }
        else {
          s.nextIterationHook.get.flatMap(isTestEnd => {
            if (isTestEnd) IO.unit
            else iteration(state) >> sendRunner(state, iterationNo + 1)
          })
        }
      })
    }

    def loop(until: Long, state: Ref[IO, State], iterationNo: Int = 0): IO[Unit] = {
      if (clock.millis() >= until) {
        state
          .get
          .flatMap(_.nextIterationHook.complete(true))
          .void
      }
      else {
        for {
          nextIterationH <- Deferred[IO, Boolean]
          _              <- state.get.flatMap(_.nextIterationHook.complete(false))
          addedPermits   <- state.modify(s => {
                              val p = newPermits(s.sent, s.permits)
                              s.copy(
                                permits = s.permits + p,
                                currentIteration = s.currentIteration + 1,
                                nextIterationHook = nextIterationH
                              ) -> p
                            })
          _              <- IO(logger.info(s"Messages to send: $addedPermits (concurrency=${config.senderConcurrency})"))
          _              <- IO.sleep(1.second)
          result         <- loop(until, state, iterationNo + 1)
        } yield result
      }
    }

    def newPermits(sent: Long, permits: Long) = {
      if (permits - sent > config.msgsPerSecond) 0
      else config.msgsPerSecond
    }

    for {
      initHook  <- Deferred[IO, Boolean]
      initState <- Ref.of[IO, State](State(0, 0, 0, initHook))
      testEnd   <- IO(clock.millis() + config.testLengthSeconds.seconds.toMillis)
      runners   <- (loop(testEnd, initState) :: (1 to senderConcurrency).map(_ => sendRunner(initState)).toList).parSequence.void
    } yield runners
  }

  private def nextMessagesBatch(batchSize: Int): Seq[String] = {
    (1 to batchSize)
      .map(_ => messagesPool.nextMessage())
      .map(Timestamp.add(_, clock))
  }
}
