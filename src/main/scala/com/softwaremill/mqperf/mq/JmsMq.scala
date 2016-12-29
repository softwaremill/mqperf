package com.softwaremill.mqperf.mq

import javax.jms._
import scala.annotation.tailrec

trait JmsMq extends Mq {
  def configMap: Map[String, String]
  def connectionFactory: ConnectionFactory

  val QueueName = "mq"

  override type MsgId = Message

  override def close() {}

  override def createSender() = new MqSender {
    val connection = connectionFactory.createConnection("admin", "admin")
    connection.start()

    val session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE)

    val destination = session.createQueue(QueueName)

    val producer = session.createProducer(destination)
    producer.setDeliveryMode(DeliveryMode.PERSISTENT)

    override def send(msgs: List[String]) {
      msgs.foreach(msg => producer.send(session.createTextMessage(msg)))
    }

    override def close(): Unit = {
      session.close()
      connection.close()
    }
  }

  override def createReceiver() = new MqReceiver {
    val connection = connectionFactory.createConnection("admin", "admin")
    connection.start()

    val session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE)

    val destination = session.createQueue(QueueName)

    val consumer = session.createConsumer(destination)

    override def receive(maxMsgCount: Int) = {
      doReceive(Nil, 1000L, maxMsgCount)
    }

    @tailrec
    private def doReceive(acc: List[(MsgId, String)], waitForMsgs: Long, count: Int): List[(MsgId, String)] = {
      if (count == 0) {
        acc
      }
      else {
        val message = consumer.receive(waitForMsgs)
        if (message == null) {
          acc
        }
        else {
          doReceive((message, message.asInstanceOf[TextMessage].getText) :: acc, 100L, count - 1)
        }
      }
    }

    override def ack(ids: List[MsgId]) = {
      ids.foreach { id =>
        id.acknowledge()
      }
    }

    override def close(): Unit = {
      session.close()
      connection.close()
    }
  }
}
