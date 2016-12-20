package com.softwaremill.mqperf.mq.kafka

import java.util.{Map => JMap}
import akka.actor.{Actor, ActorLogging, Cancellable}
import com.softwaremill.mqperf.mq.kafka.PeriodicKafkaCommitter.{CommitOffsets, DoCommit}
import org.apache.kafka.clients.consumer.{KafkaConsumer, OffsetAndMetadata, OffsetCommitCallback}
import org.apache.kafka.common.TopicPartition
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration

/**
 * A simplified committer for the purpose of running benchmarks. No retry logic, no error handling, etc.
 */
private[kafka] class PeriodicKafkaCommitter(commitInterval: FiniteDuration, consumer: KafkaConsumer[_, _])
    extends Actor with ActorLogging {

  var offsetsToCommit: CommitOffsets = Map.empty
  var scheduledCommit: Cancellable = _

  override def preStart(): Unit = {
    super.preStart()
    scheduledCommit = context.system.scheduler.schedule(commitInterval, commitInterval, self, DoCommit)
  }

  override def receive: Receive = {
    case o: Map[_, _] =>
      offsetsToCommit = offsetsToCommit ++ o.asInstanceOf[CommitOffsets]
    case DoCommit =>
      log.info("Received commit request.")
      if (offsetsToCommit.nonEmpty) {
        log.info(s"Committing offsets: $offsetsToCommit")
        consumer.commitAsync(offsetsToCommit.mapValues(new OffsetAndMetadata(_)), new OffsetCommitCallback {
          override def onComplete(offsets: JMap[TopicPartition, OffsetAndMetadata], exception: Exception): Unit = {
            if (exception != null) {
              log.error(exception, "Commit failed")
              throw exception
            }
          }
        })
        offsetsToCommit = Map.empty
      }
  }

  override def postStop(): Unit = {
    scheduledCommit.cancel()
    super.postStop()
  }
}

object PeriodicKafkaCommitter {
  type CommitOffsets = Map[TopicPartition, Long]
  val EmptyOffsetMap = Map.empty[TopicPartition, Long]
  case object DoCommit
}
