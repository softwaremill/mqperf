package com.softwaremill.mqperf.mq

import java.util.concurrent.ConcurrentLinkedQueue

import com.rabbitmq.client._
import com.softwaremill.mqperf.config.TestConfig

import scala.annotation.tailrec

class RabbitMq(testConfig: TestConfig) extends Mq {

  private val QueueName = "quorum.mqperf"

  private val cf = new ConnectionFactory()
  cf.setHost(testConfig.brokerHosts.head)
  cf.setUsername("guest")
  cf.setPassword("guest")
  private val conn = cf.newConnection()

  override type MsgId = Long

  def newChannel(): Channel = {
    val channel = conn.createChannel()
    channel.queueDeclare(QueueName, true, false, false, null)
    channel
  }

  override def close() {
    conn.close()
  }

  override def createSender() =
    new MqSender {
      private val channel = newChannel()
      channel.confirmSelect() // publisher acks

      override def send(msgs: List[String]) {
        msgs.foreach { msg =>
          channel.basicPublish("", QueueName, MessageProperties.PERSISTENT_TEXT_PLAIN, msg.getBytes)
        }

        channel.waitForConfirms()
      }
    }

  override def createReceiver() =
    new MqReceiver {
      private val channel = newChannel()
      channel.basicQos(testConfig.mqConfig.getInt("qos"), true) // fair dispatch - up to `qos` unack can be received

      // unbounded queue - but we'll only get up to `qos` entries
      private val queue = new ConcurrentLinkedQueue[(MsgId, String)]()

      private val consumer = new DefaultConsumer(channel) {
        override def handleDelivery(
            consumerTag: String,
            envelope: Envelope,
            properties: AMQP.BasicProperties,
            body: Array[Byte]
        ): Unit = {

          queue.add((envelope.getDeliveryTag, new String(body, "UTF-8")))
        }
      }

      channel.basicConsume(QueueName, false, consumer)

      override def receive(maxMsgCount: Int): List[(Long, String)] = {
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
            val next = queue.poll()
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

      private val multipleAck = testConfig.mqConfig.getBoolean("multiple_ack")

      override def ack(ids: List[MsgId]): Unit = {
        if (multipleAck) {
          if (ids.nonEmpty) {
            // as on optimization, we acknowledge multiple messages at once (http://www.rabbitmq.com/confirms.html)
            // This works as delivery tags are channel scoped (and we use one channel per receiver), and we know
            // that all messages in a batch are acknowledged at once.
            channel.basicAck(ids.max, true)
          }
        } else {
          ids.foreach { id =>
            channel.basicAck(id, false)
          }
        }
      }
    }
}
