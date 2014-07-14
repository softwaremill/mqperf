package com.softwaremill.mqperf.mq

import java.util.Properties
import java.util.concurrent.{ConcurrentLinkedQueue, Semaphore}

import kafka.consumer.{Consumer, ConsumerConfig, ConsumerTimeoutException, KafkaStream}
import kafka.producer.{KeyedMessage, Producer, ProducerConfig}
import kafka.serializer.StringDecoder

import scala.util.Random

class KafkaMq(configMap: Map[String, String]) extends Mq {
  private val GroupId = "mq-group"
  private val Topic = "mq"

  override type MsgId = String

  // assuming that the topic is created
  // replication factor: 3; number of partitions: at least the number of threads

  private var onClose = () => ()

  override def createSender() = new MqSender {
    val producersProps = new Properties()
    producersProps.put("metadata.broker.list", configMap("host"))
    producersProps.put("serializer.class", "kafka.serializer.StringEncoder")
    producersProps.put("request.required.acks", configMap("acks"))

    val producerConfig = new ProducerConfig(producersProps)
    val producer = new Producer[String, String](producerConfig)

    val r = new Random()

    override def send(msgs: List[String]) {
      producer.send(msgs.map(msg => new KeyedMessage[String, String](Topic, r.nextString(5), msg)): _*)
    }
  }

  // will only be run for receivers
  lazy val (receiverStreams, commitSemaphore) = {
    val consumerProps = new Properties()
    consumerProps.put("zookeeper.connect", configMap("zookeeper"))
    consumerProps.put("group.id", GroupId)
    consumerProps.put("auto.commit.enable", "false")
    consumerProps.put("auto.offset.reset", "smallest")
    consumerProps.put("consumer.timeout.ms", "10000") // 10 seconds

    val consumerConfig = new ConsumerConfig(consumerProps)
    val consumerConnector = Consumer.create(consumerConfig)

    // This must be the same as the number of receiver threads. We have to know the number of threads upfront.
    val consumerThreads = configMap("consumerThreads").toInt

    val commitSemaphore = new Semaphore(consumerThreads)
    val commitMs = configMap("commitMs").toLong

    val commitOffsetsThread = new Thread() {
      override def run() = {
        while (true) {
          Thread.sleep(commitMs)

          commitSemaphore.acquire(consumerThreads)
          consumerConnector.commitOffsets
          commitSemaphore.release()
        }
      }
    }
    commitOffsetsThread.setDaemon(true)
    commitOffsetsThread.start()

    onClose = () => {
      commitOffsetsThread.interrupt()
      commitOffsetsThread.join()
      consumerConnector.shutdown()
    }

    val streams = consumerConnector.createMessageStreams(
      Map(Topic -> consumerThreads),
      new StringDecoder(),
      new StringDecoder()).apply(Topic)

    val streamsQueue = new ConcurrentLinkedQueue[KafkaStream[String, String]]()
    streams.foreach(streamsQueue.offer)

    (streamsQueue, commitSemaphore)
  }

  override def createReceiver() = new MqReceiver {
    val it = receiverStreams.poll().iterator()

    override def receive(maxMsgCount: Int) = {
      commitSemaphore.acquire()

      var msgs: List[(String, String)] = Nil

      val result = try {
        while (msgs.size < maxMsgCount && it.hasNext()) {
          val msg = it.next().message()
          msgs = (msg, msg) :: msgs
        }

        msgs
      } catch {
        case e: ConsumerTimeoutException => msgs
        case e: Exception => {
          commitSemaphore.release()
          throw e
        }
      }

      if (result.size == 0) {
        commitSemaphore.release()
      }

      result
    }

    override def ack(ids: List[MsgId]) {
      // ids are ignored - we allow ack-ing all received messages asynchronously
      commitSemaphore.release()
    }
  }

  override def close() = {
    onClose()
  }
}

object KafkaMqTest {
  val config = Map(
    "host" -> "localhost:9092",
    "acks" -> "1",
    "zookeeper" -> "localhost:2181",
    "consumerThreads" -> "1",
    "commitMs" -> "1000")
}

object KafkaMqTestSend extends App {
  val mq = new KafkaMq(KafkaMqTest.config)

  val sender = mq.createSender()
  sender.send(List("1a", "2b", "3c"))
  sender.close()

  mq.close()
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