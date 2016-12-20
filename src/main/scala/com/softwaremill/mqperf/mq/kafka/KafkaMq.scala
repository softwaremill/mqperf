package com.softwaremill.mqperf.mq.kafka

import java.util
import java.util.{Properties, UUID}
import akka.actor.{ActorSystem, Props}
import com.softwaremill.mqperf.mq.Mq
import com.softwaremill.mqperf.{ReceiverRunnable, ReportResults}
import com.typesafe.scalalogging.StrictLogging
import org.apache.kafka.clients.consumer.{ConsumerRecord, KafkaConsumer}
import org.apache.kafka.clients.producer._
import org.apache.kafka.common.TopicPartition
import scala.collection.JavaConversions._
import scala.annotation.tailrec
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

class KafkaMq(configMap: Map[String, String]) extends Mq with StrictLogging {
  private val GroupId = "mq-group"
  private val Topic = "mq"
  val pollTimeout = 50 millis
  lazy val system = ActorSystem(s"KafkaTest-${UUID.randomUUID().toString}")
  override type MsgId = (TopicPartition, Long)

  private var onClose = () => {
    Await.result(system.terminate(), atMost = 30 seconds)
  }

  override def createSender() = new MqSender {
    var sendingMsg = false
    val producersProps = new Properties()
    producersProps.put("bootstrap.servers", configMap("hosts"))
    producersProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    producersProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    producersProps.put("acks", configMap("acks"))
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

    // will only be run for receivers
    lazy val consumer = {
      val consumerProps = new Properties()
      consumerProps.put("bootstrap.servers", configMap("hosts"))
      consumerProps.put("group.id", GroupId)
      consumerProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
      consumerProps.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
      consumerProps.put("enable.auto.commit", "false")
      consumerProps.put("auto.offset.reset", "earliest")
      val consumer = new KafkaConsumer[String, String](consumerProps)
      consumer.subscribe(List(Topic))
      consumer
    }
    lazy val committerActor = {
      val commitMs = configMap("commitMs").toLong.millis
      system.actorOf(Props(new PeriodicKafkaCommitter(commitMs, consumer)))
    }

    var buffer: java.util.Iterator[ConsumerRecord[String, String]] = new java.util.ArrayList[ConsumerRecord[String, String]]().iterator()


    override def receive(msgCount: Int): List[(MsgId, String)] = {
      @tailrec
      def nextMsg(): ConsumerRecord[String, String] = {
        if (!buffer.hasNext) {
          buffer = consumer.poll(pollTimeout.toMillis).iterator
          nextMsg()
        }
        else
          buffer.next()
      }

      val result = for (_ <- 0 until msgCount) yield {
          val msg = nextMsg()
          val msgId = (new TopicPartition(msg.topic(), msg.partition()), msg.offset())
          (msgId, msg.value())
        }

      result.toList
    }

    override def ack(ids: List[MsgId]): Unit = {
      logger.info(s"ACKing msgs: $ids")
      val commitRequest =ids.foldLeft(PeriodicKafkaCommitter.EmptyOffsetMap) {
        case (offsetMap, (topicPartition, offset)) => offsetMap + (topicPartition -> (offset + 1))
      }
      committerActor ! commitRequest
    }

    override def close(): Unit = {
      system.stop(committerActor)
      consumer.close()
      super.close()
    }
  }


  override def close(): Unit = {
    onClose()
  }
}

object KafkaMqTest {
  val config = Map(
    "hosts" -> "localhost:9092",
    "key.serializer" -> "",
    "acks" -> "1",
    "zookeeper" -> "localhost:2181",
    "commitMs" -> "1000")
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
  val report = new ReportResults("x")
  val rr = new ReceiverRunnable(
    mq, report, 10
  )

  val threads = (1 to 2).map { _ =>
    val t = new Thread(rr)
    t.start()
    t
  }

  threads.foreach(_.join())

  mq.close()
}