package com.softwaremill.mqperf.mq

import java.util.Properties
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.ReentrantReadWriteLock

import com.softwaremill.mqperf.{ReceiverRunnable, ReportResults}
import com.typesafe.scalalogging.StrictLogging
import kafka.consumer.{Consumer, ConsumerConfig, ConsumerTimeoutException, KafkaStream}
import kafka.producer.{KeyedMessage, Producer, ProducerConfig}
import kafka.serializer.StringDecoder

import scala.util.Random

class KafkaMq(configMap: Map[String, String]) extends Mq with StrictLogging {
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
  lazy val (receiverStreams, commitLock) = {
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

    val commitLock = new ReentrantReadWriteLock()
    val commitMs = configMap("commitMs").toLong

    val commitOffsetsThread = new Thread() {
      override def run() = {
        while (true) {
          Thread.sleep(commitMs)
          commitLock.writeLock().lockInterruptibly()
          logger.info("Commiting offsets")
          consumerConnector.commitOffsets
          commitLock.writeLock().unlock()
        }
      }
    }
    commitOffsetsThread.setDaemon(true)
    commitOffsetsThread.start()

    onClose = () => {
      commitOffsetsThread.interrupt()
      commitOffsetsThread.join()

      consumerConnector.commitOffsets

      consumerConnector.shutdown()
    }

    val streams = consumerConnector.createMessageStreams(
      Map(Topic -> consumerThreads),
      new StringDecoder(),
      new StringDecoder()).apply(Topic)

    val streamsQueue = new ConcurrentLinkedQueue[KafkaStream[String, String]]()
    streams.foreach(streamsQueue.offer)

    (streamsQueue, commitLock)
  }

  override def createReceiver() = new MqReceiver {
    val it = receiverStreams.poll().iterator()

    override def receive(maxMsgCount: Int) = {
      commitLock.readLock().lockInterruptibly()

      var msgs: List[(String, String)] = Nil

      val result = try {
        while (msgs.size < maxMsgCount && it.hasNext()) {
          val msg = it.next().message()
          msgs = (msg, msg) :: msgs
        }

        msgs
      } catch {
        case e: ConsumerTimeoutException => msgs
        case e: Exception =>
          commitLock.readLock().unlock()
          throw e
      }

      if (result.size == 0) {
        commitLock.readLock().unlock()
      }

      result
    }

    override def ack(ids: List[MsgId]) {
      // ids are ignored - we allow ack-ing all received messages asynchronously
      commitLock.readLock().unlock()
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
    "consumerThreads" -> "2",
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