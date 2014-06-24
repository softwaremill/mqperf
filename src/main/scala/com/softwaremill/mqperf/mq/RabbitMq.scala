package com.softwaremill.mqperf.mq

import com.rabbitmq.client.{QueueingConsumer, MessageProperties, ConnectionFactory, AMQP}

import scala.annotation.tailrec

class RabbitMq(configMap: Map[String, String]) extends Mq {

  val QueueName = "mq"

  val props = new AMQP.BasicProperties.Builder().deliveryMode(2).contentType("text/plain").build()
  val cf = new ConnectionFactory()
  cf.setHost(configMap("host"))
  val conn = cf.newConnection()

  override type MsgId = Long

  def newChannel() = {
    val channel = conn.createChannel()
    channel.queueDeclare(QueueName, true, false, false, null)
    channel
  }

  override def close() {
    conn.close()
  }

  override def createSender() = new MqSender {
    val channel = newChannel()
    channel.confirmSelect() // publisher acks

    override def send(msgs: List[String]) {
      msgs.foreach { msg =>
        channel.basicPublish("", QueueName, MessageProperties.PERSISTENT_TEXT_PLAIN, msg.getBytes)
      }

      channel.waitForConfirms()
    }

    override def close() {
      channel.close()
    }
  }

  override def createReceiver() = new MqReceiver {
    val channel = newChannel()
    channel.basicQos(configMap("qos").toInt, true) // fair dispatch - up to 10 unack can be received

    val consumer = new QueueingConsumer(channel)
    channel.basicConsume(QueueName, false, consumer)

    override def receive(maxMsgCount: Int) = {
      doReceive(Nil, 1000L, maxMsgCount)
    }

    @tailrec
    private def doReceive(acc: List[(MsgId, String)], waitForMsgs: Long, count: Int): List[(MsgId, String)] = {
      if (count == 0) {
        acc
      } else {
        val delivery = consumer.nextDelivery(1000L)
        if (delivery == null) {
          acc
        } else {
          doReceive((delivery.getEnvelope.getDeliveryTag, new String(delivery.getBody)) :: acc, 100L, count-1)
        }
      }
    }

    override def ack(ids: List[MsgId]) = {
      ids.foreach { id =>
        channel.basicAck(id, false)
      }
    }

    override def close() {
      channel.close()
    }
  }
}