package com.softwaremill.mqperf.mq

import java.util
import java.util.Random
import java.util.concurrent.{ConcurrentLinkedQueue, Semaphore}

import com.softwaremill.mqperf.config.TestConfig
import com.typesafe.scalalogging.StrictLogging
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer
import org.apache.rocketmq.client.consumer.listener.{ConsumeConcurrentlyContext, ConsumeConcurrentlyStatus, MessageListenerConcurrently}
import org.apache.rocketmq.client.producer.{DefaultMQProducer, SendStatus}
import org.apache.rocketmq.common.consumer.ConsumeFromWhere
import org.apache.rocketmq.common.message.{Message, MessageExt}
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel

import scala.annotation.tailrec
import scala.collection.JavaConverters._

class RocketMq(testConfig: TestConfig) extends Mq with StrictLogging {
  override type MsgId = Semaphore

  val nameServers = testConfig.brokerHosts.map(_ + ":9876").mkString(";")
  val random = new Random()

  var producer: DefaultMQProducer = null
  def startProducer(): Unit = synchronized {
    if (producer == null) {
      producer = new DefaultMQProducer("mqperf_producer_group")
      producer.setNamesrvAddr(nameServers)
      producer.start()
    }
  }

  val queue = new ConcurrentLinkedQueue[(Semaphore, String)]()
  var consumer: DefaultMQPushConsumer = null
  def startConsumer(): Unit = synchronized {
    if (consumer == null) {
      consumer = new DefaultMQPushConsumer("mqperf_consumer_group")
      consumer.setNamesrvAddr(nameServers)
      consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET)
      consumer.setMessageModel(MessageModel.CLUSTERING)
      consumer.subscribe("mqperf", "*")
      consumer.setInstanceName("instance" + random.nextInt(1000))
      consumer.registerMessageListener(new MessageListenerConcurrently {
        override def consumeMessage(
          msgs: util.List[MessageExt],
          context: ConsumeConcurrentlyContext
        ): ConsumeConcurrentlyStatus = {

          val ms = msgs.asScala.map { m =>
            val s = new Semaphore(0)
            (s, new String(m.getBody, "UTF-8"))
          }

          ms.foreach(queue.add)
          ms.foreach(_._1.acquire())
          ConsumeConcurrentlyStatus.CONSUME_SUCCESS
        }
      })
      consumer.start()
    }
  }

  override def createSender() = new MqSender {
    startProducer()

    override def send(msgs: List[String]): Unit = {
      val ms = msgs.map(m => new Message("mqperf", "tag" + random.nextInt(10), random.nextInt().toString, m.getBytes("UTF-8")))
      val r = producer.send(ms.asJava)
      if (r.getSendStatus != SendStatus.SEND_OK) {
        logger.error(s"Error while sending: $r")
      }
    }
  }

  override def createReceiver() = new MqReceiver {
    startConsumer()

    override def receive(maxMsgCount: Int): List[(MsgId, String)] = {
      @tailrec
      def doReceive(acc: List[(MsgId, String)], count: Int): List[(MsgId, String)] = {
        if (count == 0) {
          acc
        }
        else {
          nextMessageFromQueue(waitForMsgs = acc.isEmpty) match {
            case None => acc
            case Some(m) => doReceive(m :: acc, count - 1)
          }
        }
      }

      def nextMessageFromQueue(waitForMsgs: Boolean): Option[(MsgId, String)] = {
        @tailrec
        def doPoll(waitIterations: Int): Option[(MsgId, String)] = {
          val next = queue.poll()
          if (next == null) {
            if (waitIterations > 0) {
              Thread.sleep(100L)
              doPoll(waitIterations - 1)
            }
            else None
          }
          else Some(next)
        }

        doPoll(if (waitForMsgs) 10 else 0)
      }

      doReceive(Nil, maxMsgCount)
    }

    override def ack(ids: List[Semaphore]): Unit = {
      ids.foreach(_.release())
    }

  }

  override def close(): Unit = {
    if (producer != null) {
      producer.shutdown()
    }

    if (consumer != null) {
      consumer.shutdown()
    }
  }
}

object RMSender extends App {
  val mq = new RocketMq(TestConfig("", "", 0, 0, 0, 0, 0, 0, List("localhost"), "", null))
  mq.createSender().send(List("a", "b", "c", "d"))
  println("SENT")
  mq.close()
}

object RMReceiver extends App {
  val mq = new RocketMq(TestConfig("", "", 0, 0, 0, 0, 0, 0, List("localhost"), "", null))
  val rcv = mq.createReceiver()
  for (i <- 1 to 100) {
    val r = rcv.receive(10)
    println("GOT " + r)
    rcv.ack(r.map(_._1))
    Thread.sleep(1000)
  }
  mq.close()
}