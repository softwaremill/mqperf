package com.softwaremill.mqperf.mq

import javax.jms._

import com.softwaremill.mqperf.config.TestConfig

import scala.annotation.tailrec

trait JmsMq extends Mq {
  def connectionFactory: ConnectionFactory

  val QueueName = "mq"

  override type MsgId = Message

  override def close() {}

  protected def testConfig: TestConfig

  override def createSender() =
    new MqSender {
      private val isTransacted = testConfig.mqConfig.getBoolean("transacted")

      private val connection = connectionFactory.createConnection("admin", "admin")
      connection.start()

      private val session = connection.createSession(isTransacted, Session.CLIENT_ACKNOWLEDGE)

      private val destination = session.createQueue(QueueName)

      private val producer = session.createProducer(destination)
      producer.setDeliveryMode(DeliveryMode.PERSISTENT)

      override def send(msgs: List[String]) {
        msgs.foreach(msg => producer.send(session.createTextMessage(msg)))
        if (isTransacted) session.commit()
      }

      override def close(): Unit = {
        session.close()
        connection.close()
      }
    }

  override def createReceiver() =
    new MqReceiver {
      private val connection = connectionFactory.createConnection("admin", "admin")
      connection.start()

      private val session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE)

      private val destination = session.createQueue(QueueName)

      private val consumer = session.createConsumer(destination)

      override def receive(maxMsgCount: Int): List[(Message, String)] = {
        doReceive(Nil, waitForMsgs = true, maxMsgCount)
      }

      @tailrec
      private def doReceive(acc: List[(MsgId, String)], waitForMsgs: Boolean, count: Int): List[(MsgId, String)] = {
        if (count == 0) {
          acc
        } else {
          val message = if (waitForMsgs) consumer.receive(1000L) else consumer.receiveNoWait()
          if (message == null) {
            acc
          } else {
            doReceive((message, message.asInstanceOf[TextMessage].getText) :: acc, waitForMsgs = false, count - 1)
          }
        }
      }

      override def ack(ids: List[MsgId]): Unit = {
        if (ids.nonEmpty) {
          ids.last.acknowledge()
        }
      }

      override def close(): Unit = {
        session.close()
        connection.close()
      }
    }
}
