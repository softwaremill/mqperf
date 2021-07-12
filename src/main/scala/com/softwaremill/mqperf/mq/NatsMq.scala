package com.softwaremill.mqperf.mq

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch}

import com.amazonaws.util.EC2MetadataUtils
import com.softwaremill.mqperf.config.TestConfig
import com.typesafe.scalalogging.StrictLogging
import io.nats.client.Nats
import io.nats.streaming.{Message, NatsStreaming, SubscriptionOptions}

import scala.annotation.tailrec
import scala.util.Random

class NatsMq(testConfig: TestConfig) extends Mq with StrictLogging {

  private val subject = "mqperf"
  private val clusterId = "mqperf-cluster"
  private val durableName = "mqperf-durable"
  private val queueName = "mqper-queue"

  private val natsOptions = {
    val builder = new io.nats.client.Options.Builder()
    testConfig.brokerHosts.foreach(host => builder.server(s"nats://$host:4222"))
    builder.build()
  };
  private val natsConnection = Nats.connect(natsOptions)

  private val natsStreamingOptions = new io.nats.streaming.Options.Builder().natsConn(natsConnection).build()

  private val r = new Random()
  private val clientIdBase = EC2MetadataUtils.getInstanceId // s"x${r.nextInt()}" //
  private def newNatsStreamingConnection = {
    val clientId = clientIdBase + NatsMq.clientCounter.incrementAndGet()
    logger.info("Using client id: " + clientId)
    NatsStreaming.connect(clusterId, clientId, natsStreamingOptions)
  }

  override type MsgId = Message

  override def createSender(): MqSender =
    new MqSender() {
      private val c = newNatsStreamingConnection

      override def send(msgs: List[String]): Unit = {
        val acks = new CountDownLatch(msgs.size)
        msgs.foreach(msg =>
          c.publish(subject, msg.getBytes("UTF-8"), (nuid: String, ex: Exception) => acks.countDown())
        )

        acks.await()
      }

      override def close(): Unit = {
        c.close()
      }
    }

  private val receiveBuffer = new ConcurrentLinkedQueue[(Message, String)]()

  override def createReceiver(): MqReceiver =
    new MqReceiver() {
      private val c = newNatsStreamingConnection

      private val subscription = c.subscribe(
        subject,
        queueName,
        (msg: Message) => receiveBuffer.add((msg, new String(msg.getData, "UTF-8"))),
        new SubscriptionOptions.Builder()
          .durableName(durableName)
          .manualAcks()
          .build()
      )

      override def receive(maxMsgCount: Int): List[(Message, String)] = {
        @tailrec
        def doReceive(acc: List[(MsgId, String)], count: Int): List[(MsgId, String)] = {
          if (count == 0) {
            acc
          } else {
            nextMessageFromQueue(waitForMsgs = acc.isEmpty) match {
              case None    => acc
              case Some(m) => doReceive(m :: acc, count - 1)
            }
          }
        }

        def nextMessageFromQueue(waitForMsgs: Boolean): Option[(MsgId, String)] = {
          @tailrec
          def doPoll(waitIterations: Int): Option[(MsgId, String)] = {
            val next = receiveBuffer.poll()
            if (next == null) {
              if (waitIterations > 0) {
                Thread.sleep(100L)
                doPoll(waitIterations - 1)
              } else None
            } else Some(next)
          }

          doPoll(if (waitForMsgs) 10 else 0)
        }

        doReceive(Nil, maxMsgCount)
      }

      override def ack(ids: List[Message]): Unit = {
        ids.foreach(_.ack())
      }

      override def close(): Unit = {
        subscription.close()
        c.close()
      }

    }

  override def close(): Unit = {
    natsConnection.close()
  }
}

object NatsMq {
  private val clientCounter = new AtomicInteger(0)
}

object Test extends App {
  val mq = new NatsMq(new TestConfig("", "", 1, 100, 100, 10, 1, 10, List("127.0.0.1"), "", null))

  val sender1 = mq.createSender()
  val sender2 = mq.createSender()
  sender1.send((1 to 5).map(i => s"msgA$i").toList)
  sender2.send((1 to 5).map(i => s"msgB$i").toList)

  sender1.close()
  sender2.close()
  mq.close()
}

object Test2 extends App {
  val mq = new NatsMq(new TestConfig("", "", 1, 100, 100, 10, 1, 10, List("127.0.0.1"), "",  null))

  val receiver1 = mq.createReceiver()
  while (true) {
    println("RECEIVING")
    val msgs = receiver1.receive(3)
    msgs.foreach(msg => println(s"MSG: ${msg._2}"))
    receiver1.ack(msgs.map(_._1))
    Thread.sleep(1000)
  }

  receiver1.close()
  mq.close()
}
