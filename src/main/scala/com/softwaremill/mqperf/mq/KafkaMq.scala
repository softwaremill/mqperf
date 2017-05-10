package com.softwaremill.mqperf.mq

import java.util.{Properties, Map => JMap}

import com.codahale.metrics.MetricRegistry
import com.softwaremill.mqperf.{ReceiverRunnable, DynamoReportResults}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import org.apache.kafka.clients.consumer.{ConsumerRecord, KafkaConsumer, OffsetAndMetadata, OffsetCommitCallback}
import org.apache.kafka.clients.producer._
import org.apache.kafka.common.TopicPartition
import org.joda.time.DateTime

import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

class KafkaMq(val config: Config) extends Mq with StrictLogging {

  import KafkaMq._

  private val GroupId = "mq-group2"
  private val Topic = "mq2"
  val pollTimeout = 50.millis.toMillis
  val commitNs = config.getLong("commitMs").millis.toNanos

  override type MsgId = (TopicPartition, Long)

  override def createSender() = new MqSender {
    var sendingMsg = false
    val producersProps = new Properties()
    producersProps.put("bootstrap.servers", config.getString("hosts"))
    producersProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    producersProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    producersProps.put("acks", config.getString("acks"))
    val producer = new KafkaProducer[String, String](producersProps)

    val r = new Random()

    @tailrec
    override def send(msgs: List[String]): Unit = {
      if (sendingMsg)
        send(msgs)
      else if (msgs.nonEmpty) {
        sendingMsg = true
        producer.send(new ProducerRecord[String, String](Topic, r.nextString(5), msgs.head), new Callback {
          override def onCompletion(metadata: RecordMetadata, exception: Exception): Unit = {
            // This callback is executed on the same thread, so we can safely access mutable state
            sendingMsg = false
          }
        })
        send(msgs.tail)
      }
    }
  }

  override def createReceiver() = new MqReceiver {

    var offsetsToCommit: CommitOffsets = KafkaMq.EmptyOffsetMap
    var lastCommitTick = System.nanoTime()

    // will only be run for receivers
    lazy val consumer = {
      val consumerProps = new Properties()
      consumerProps.put("bootstrap.servers", config.getString("hosts"))
      consumerProps.put("group.id", GroupId)
      consumerProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
      consumerProps.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
      consumerProps.put("enable.auto.commit", "false")
      consumerProps.put("auto.offset.reset", "earliest")
      val consumer = new KafkaConsumer[String, String](consumerProps)
      consumer.subscribe(List(Topic))
      consumer
    }

    def timeToCommit(): Boolean = System.nanoTime() - lastCommitTick > commitNs

    def commitAsync(): Unit = {
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

    var it: java.util.Iterator[ConsumerRecord[String, String]] = consumer.poll(pollTimeout).iterator()

    override def receive(maxMsgCount: Int): List[(MsgId, String)] = {
      var msgs: List[(MsgId, String)] = Nil
      if (timeToCommit())
        commitAsync()
      if (!it.hasNext)
        it = consumer.poll(pollTimeout).iterator()

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

object KafkaMqTest {
  val config = ConfigFactory.parseMap(Map(
    "hosts" -> "localhost:9092",
    "key.serializer" -> "",
    "acks" -> "1",
    "zookeeper" -> "localhost:2181",
    "commitMs" -> "1000"
  ).asJava)
}

object KafkaMqTestSend extends App {
  val mq = new KafkaMq(KafkaMqTest.config)

  val start = System.currentTimeMillis()

  val sender = mq.createSender()
  for (i <- 1 to 100000) {
    sender.send(List("a", "b", "c", "d", "e", "f", "g", "h", "i", "j").map(_ + i))
  }
  sender.close()

  mq.close()

  println("Done " + (System.currentTimeMillis() - start))
}

object KafkaMqTestReceive extends App {
  val mq = new KafkaMq(KafkaMqTest.config)

  val receiver = mq.createReceiver()
  val msgs = receiver.receive(2)
  println(msgs.map(_._2))
  receiver.ack(msgs.map(_._1))

  Thread.sleep(5000L)

  receiver.close()

  mq.close()
}

object KafkaMqTestReceive2 extends App {
  println(s"Starting test (receiver)")

  val mq = new KafkaMq(KafkaMqTest.config)
  val report = new DynamoReportResults("x", "x")
  val rr = new ReceiverRunnable(mq, report, "kafka", 10, new MetricRegistry, new DateTime(), 1)

  val threads = (1 to 2).map { _ =>
    val t = new Thread(rr)
    t.start()
    t
  }

  threads.foreach(_.join())

  mq.close()
}
