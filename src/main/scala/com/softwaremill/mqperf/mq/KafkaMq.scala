package com.softwaremill.mqperf.mq

import java.util.Properties
import java.util.concurrent.{ConcurrentLinkedQueue, Executors}

import kafka.consumer.{Consumer, ConsumerConfig}
import kafka.producer.{KeyedMessage, ProducerConfig, Producer}
import kafka.serializer.StringDecoder

import scala.annotation.tailrec

class KafkaMq(configMap: Map[String, String]) extends Mq {
  private val GroupId = "mq-group"
  private val Topic = "mq"

  override type MsgId = String

  override def createSender() = new MqSender {
    val producersProps = new Properties()
    producersProps.put("metadata.broker.list", configMap("host"))
    producersProps.put("serializer.class", "kafka.serializer.StringEncoder")
    producersProps.put("request.required.acks", configMap("acks"))

    val producerConfig = new ProducerConfig(producersProps)
    val producer = new Producer[String, String](producerConfig)

    override def send(msgs: List[String]) {
      producer.send(msgs.map(msg => new KeyedMessage[String, String](Topic, msg)): _*)
    }
  }

  override def createReceiver() = new MqReceiver {
    val consumerProps = new Properties()
    consumerProps.put("zookeeper.connect", configMap("zookeeper"))
    consumerProps.put("group.id", GroupId)
    consumerProps.put("auto.commit.interval.ms", "1000")

    val consumerConfig = new ConsumerConfig(consumerProps)
    val consumerConnector = Consumer.create(consumerConfig)

    val consumerThreads = configMap("consumerThreads").toInt

    // with Kafka consumers, thread number must be known upfront, so we are always going to use 1 receiver
    // thread, and create threads here.
    val messageStreams = consumerConnector.createMessageStreams(
      Map(Topic -> consumerThreads),
      new StringDecoder(),
      new StringDecoder()).apply(Topic)

    val executor = Executors.newFixedThreadPool(consumerThreads)

    val msgQueue = new ConcurrentLinkedQueue[String]()

    messageStreams.foreach { stream =>
      executor.submit(new Runnable() {
        override def run() = {
          val it = stream.iterator()
          while (it.hasNext()) {
            msgQueue.add(it.next().message())
          }
        }
      })
    }

    override def receive(maxMsgCount: Int) = doReceive(Nil, waitForMsgs = true, maxMsgCount)

    @tailrec
    private def doReceive(acc: List[(MsgId, String)], waitForMsgs: Boolean, count: Int): List[(MsgId, String)] = {
      if (count == 0) {
        acc
      } else {
        val msg = msgQueue.poll()
        if (msg == null) {
          if (waitForMsgs) {
            Thread.sleep(1000L)
            doReceive(acc, waitForMsgs, count)
          } else {
            acc
          }
        } else {
          doReceive((msg, msg) :: acc, waitForMsgs = false, count-1)
        }
      }
    }

    override def ack(ids: List[MsgId]) {
      // not supported by the high-level Kafka consumer
    }

    override def close() {
      consumerConnector.shutdown()
      executor.shutdownNow()
    }
  }
}

object KafkaMqTestSend extends App {
  val config = Map("host" -> "localhost:9092", "acks" -> "1", "zookeeper" -> "", "consumerThreads" -> "1")
  val mq = new KafkaMq(config)

  val sender = mq.createSender()
  sender.send(List("1a", "2b", "3c"))
  sender.close()

  mq.close()
}