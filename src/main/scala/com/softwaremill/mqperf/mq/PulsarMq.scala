package com.softwaremill.mqperf.mq

import java.util
import java.util.concurrent.TimeUnit

import com.softwaremill.mqperf.config.TestConfig
import com.typesafe.scalalogging.StrictLogging
import org.apache.pulsar.client.api._

import scala.collection.JavaConverters._
import scala.language.postfixOps

class PulsarMq(testConfig: TestConfig) extends Mq with StrictLogging {

  private val PulsarSubscriptionId = "mqperf-group"
  private val Topic = "mqperf"

  private def pulsarHosts = testConfig.brokerHosts.map(_ + ":6650").mkString(",")

  val pulsarClient = PulsarClient.builder()
    .serviceUrl(s"pulsar://$pulsarHosts")
    .build()

  override type MsgId = Message[Array[Byte]]

  override def createSender() =
    new MqSender {
      val producer: Producer[Array[Byte]] = pulsarClient.newProducer
        .topic(Topic)
        .create

      override def send(msgs: List[String]): Unit = {
        msgs
          .map(msg => producer.sendAsync(msg.getBytes()))
          .foreach(_.get())
      }

      override def close(): Unit = {
        producer.close()
        super.close()
      }
    }

  override def createReceiver() =
    new MqReceiver {
      private lazy val consumer = {
        val consumer = pulsarClient.newConsumer()
          .topic(Topic)
          .subscriptionName(PulsarSubscriptionId)
          .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
          .subscriptionType(SubscriptionType.Shared)
          .batchReceivePolicy(BatchReceivePolicy.builder()
            .maxNumMessages(testConfig.receiveMsgBatchSize)
            .timeout(100, TimeUnit.MILLISECONDS)
            .build())
          .subscribe()
        consumer
      }

      override def receive(maxMsgCount: Int): List[(MsgId, String)] = {
        val messages = consumer.batchReceive()
        messages.iterator().asScala.toList.map(msg => (msg, new String(msg.getData)))
      }

      override def ack(ids: List[MsgId]): Unit = {
        consumer.acknowledge(new Messages[Array[Byte]] {
          override def size(): Int = ids.size

          override def iterator(): util.Iterator[Message[Array[Byte]]] = ids.iterator.asJava
        })
      }

      override def close(): Unit = {
        consumer.close()
        super.close()
      }
    }

  override def close(): Unit = {
    pulsarClient.close()
    super.close()
  }
}
