package com.softwaremill.mqperf.mq

import java.util.Properties
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

import com.softwaremill.kmq.{KafkaClients, KmqClient, KmqConfig, RedeliveryTracker}
import com.softwaremill.mqperf.config.TestConfig
import com.typesafe.scalalogging.StrictLogging
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer._
import org.apache.kafka.common.serialization.StringDeserializer

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

class KmqMq(testConfig: TestConfig) extends Mq with StrictLogging {

  // assuming the topics already exists, with replication factor 3 and at least as many partitions as receiver threads

  private val GroupId = "mqperf-group"
  private val RedeliveryGroupId = "mqperf-redelivery-group"
  private val Topic = "mqperf"
  private val MarkerTopic = "mqperf-markers"
  private val PollTimeoutMs = 500.millis.toMillis

  private val dropMsgPercentage = testConfig.mqConfig.getDouble("drop_percent")
  private val dropRandom = new Random()

  private def kafkaHosts = testConfig.brokerHosts.map(_ + ":9092").mkString(",")

  override type MsgId = ConsumerRecord[String, String]

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

  override def createReceiver() = {
    val kafkaClients = new KafkaClients(kafkaHosts)
    val kmqConfig = new KmqConfig(Topic, MarkerTopic, GroupId, RedeliveryGroupId, 1.minute.toMillis, 10000)

    if (!receiverCreated.getAndSet(true)) {
      // this is the first receiver that is created
      closeRedeliveryTracker.set(RedeliveryTracker.start(kafkaClients, kmqConfig).close)
    }

    new MqReceiver {
      private val kmqClient = new KmqClient[String, String](
        kmqConfig,
        kafkaClients,
        classOf[StringDeserializer], classOf[StringDeserializer],
        PollTimeoutMs
      )

      override def receive(maxMsgCount: Int): List[(ConsumerRecord[String, String], String)] = {
        val msgs = kmqClient.nextBatch().toList
        msgs.map(msg => (msg, msg.value())).filter(_ => dropRandom.nextDouble() >= dropMsgPercentage)
      }

      override def ack(msgs: List[ConsumerRecord[String, String]]): Unit = {
        msgs.foreach(kmqClient.processed)
      }
    }
  }

  private val receiverCreated = new AtomicBoolean(false)
  private val closeRedeliveryTracker = new AtomicReference[() => Unit](() => ())

  override def close(): Unit = {
    closeRedeliveryTracker.get().apply()
  }
}

