package com.softwaremill.mqperf.mq

import javax.jms.{TextMessage, Message, DeliveryMode, Session}

import org.apache.activemq.ActiveMQConnectionFactory

import scala.annotation.tailrec

class ActiveMq(configMap: Map[String, String]) extends Mq {

  val QueueName = "mq"

  lazy val connectionFactory = {
    val cf = new ActiveMQConnectionFactory(configMap("host"))
    cf.setOptimizeAcknowledge(true)
    cf.setSendAcksAsync(true)
    cf
  }

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

object ActiveMqTestSend extends App {
  val mq = new ActiveMq(Map())

  val start = System.currentTimeMillis()

  val sender = mq.createSender()
  for (i <- 1 to 100) {
    sender.send(List("a", "b", "c", "d", "e", "f", "g", "h", "i", "j").map(_ + i))
  }
  sender.close()

  mq.close()

  println("Done " + (System.currentTimeMillis() - start))
}

object ActiveMqTestReceive extends App {
  val mq = new ActiveMq(Map())

  val receiver = mq.createReceiver()
  val msgs = receiver.receive(2000)
  println(msgs.map(_._2))
  receiver.ack(msgs.map(_._1))

  Thread.sleep(5000L)

  receiver.close()

  mq.close()
}