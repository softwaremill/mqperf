package com.softwaremill.mqperf.mq

import java.util.{Properties, Map => JMap}

import com.softwaremill.mqperf.config.TestConfig
import com.typesafe.scalalogging.StrictLogging
import org.apache.kafka.clients.consumer.{ConsumerRecord, KafkaConsumer, OffsetAndMetadata, OffsetCommitCallback}
import org.apache.kafka.clients.producer._
import org.apache.kafka.common.TopicPartition

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.language.postfixOps

class KafkaMq(testConfig: TestConfig) extends Mq with StrictLogging {

  // assuming the topic already exists, with replication factor 3 and at least as many partitions as receiver threads

  import KafkaMq._

  private val GroupId = "mqperf-group"
  private val Topic = "mqperf"
  private val PollTimeoutMs = 500.millis.toMillis

  private val commitNs = testConfig.mqConfig.getLong("commitMs").millis.toNanos

  private def kafkaHosts = testConfig.brokerHosts.map(_ + ":9092").mkString(",")

  override type MsgId = (TopicPartition, Long)

  override def createSender() = new MqSender {
    val producersProps = new Properties()
    producersProps.put("bootstrap.servers", kafkaHosts)
    producersProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    producersProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    producersProps.put("acks", testConfig.mqConfig.getString("acks"))
    val producer = new KafkaProducer[String, String](producersProps)

    override def send(msgs: List[String]): Unit = {
      msgs
        .map(msg => producer.send(new ProducerRecord[String, String](Topic, null, msg)))
        .foreach(_.get())
    }
  }

  override def createReceiver() = new MqReceiver {

    private var offsetsToCommit: CommitOffsets = KafkaMq.EmptyOffsetMap
    private var lastCommitTick = System.nanoTime()

    // will only be run for receivers
    private lazy val consumer = {
      val consumerProps = new Properties()
      consumerProps.put("bootstrap.servers", kafkaHosts)
      consumerProps.put("group.id", GroupId)
      consumerProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
      consumerProps.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
      consumerProps.put("enable.auto.commit", "false")
      consumerProps.put("auto.offset.reset", "earliest")
      val consumer = new KafkaConsumer[String, String](consumerProps)
      consumer.subscribe(List(Topic))
      consumer
    }

    private def timeToCommit(): Boolean = (System.nanoTime() - lastCommitTick) > commitNs

    private def commitAsync(): Unit = {
      logger.debug("Commit tick")
      lastCommitTick = System.nanoTime()
      if (offsetsToCommit.nonEmpty) {
        logger.info(s"Committing offsets: $offsetsToCommit")
        consumer.commitAsync(offsetsToCommit.mapValues(new OffsetAndMetadata(_)), new OffsetCommitCallback {
          override def onComplete(offsets: JMap[TopicPartition, OffsetAndMetadata], exception: Exception): Unit = {
            if (exception != null) {
              logger.error("Commit failed", exception)
              throw exception
            }
            else
              logger.debug(s"Commit successful: $offsets")
          }
        })
        offsetsToCommit = KafkaMq.EmptyOffsetMap
      }
    }

    private var it: java.util.Iterator[ConsumerRecord[String, String]] = _

    override def receive(maxMsgCount: Int): List[(MsgId, String)] = {
      var msgs: List[(MsgId, String)] = Nil
      if (timeToCommit())
        commitAsync()
      if (it == null || !it.hasNext)
        it = consumer.poll(PollTimeoutMs).iterator()

      while (msgs.size < maxMsgCount && it.hasNext) {
        val msg = it.next()
        val msgId = (new TopicPartition(msg.topic(), msg.partition()), msg.offset())

        msgs = (msgId, msg.value()) :: msgs
      }
      msgs
    }

    override def ack(ids: List[MsgId]): Unit = {
      val commitRequest = ids.foldLeft(KafkaMq.EmptyOffsetMap) {
        case (offsetMap, (topicPartition, offset)) => offsetMap + (topicPartition -> (offset + 1))
      }
      offsetsToCommit = offsetsToCommit ++ commitRequest
    }

    override def close(): Unit = {
      consumer.close()
      super.close()
    }
  }
}

object KafkaMq {
  type CommitOffsets = Map[TopicPartition, Long]
  val EmptyOffsetMap = Map.empty[TopicPartition, Long]
}
