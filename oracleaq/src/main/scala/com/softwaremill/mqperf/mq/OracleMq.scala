package com.softwaremill.mqperf.mq

import javax.jms._
import oracle.jdbc.pool.OracleDataSource
import oracle.jms.AQjmsFactory
import oracle.jms.AQjmsSession

import scala.annotation.tailrec


class OracleMq(configMap: Map[String, String]) extends Mq {

  val QueueSchema = "mqperf"
  val QueueName = "queue1"

  lazy val oracleDataSource = {
    val ds = new OracleDataSource()
    ds.setDriverType("thin")
    ds.setServerName(configMap("host"))
    ds.setPortNumber(1521)
    ds.setDatabaseName("AL32UTF8")
    ds.setUser("mqperf")
    ds.setPassword("mqperf123")
    ds
  }

  lazy val queueConnectionFactory = AQjmsFactory.getQueueConnectionFactory(oracleDataSource)

  override type MsgId = Message

  override def close() {}

  override def createSender() = new MqSender {

    val connection = queueConnectionFactory.createQueueConnection()
    val session = connection.createQueueSession(false, Session.CLIENT_ACKNOWLEDGE) match {
      case s: AQjmsSession => s
      case _ => throw new ClassCastException
    }

    connection.start()

    val destination = session.getQueue(QueueSchema, QueueName)

    val producer = session.createSender(destination)
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

    val connection = queueConnectionFactory.createQueueConnection()
    val session = connection.createQueueSession(false, Session.CLIENT_ACKNOWLEDGE) match {
      case s: AQjmsSession => s
      case _ => throw new ClassCastException
    }

    connection.start()

    val destination = session.getQueue(QueueSchema, QueueName)

    val consumer = session.createReceiver(destination)

    override def receive(maxMsgCount: Int) = {
      doReceive(Nil, 1000L, maxMsgCount)
    }

    @tailrec
    private def doReceive(acc: List[(MsgId, String)], waitForMsgs: Long, count: Int): List[(MsgId, String)] = {
      if (count == 0) {
        acc
      } else {
        val message = consumer.receive(waitForMsgs)
        if (message == null) {
          acc
        } else {
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

object OracleMqTestReceive extends App {

  val mq = new OracleMq(Map("host" -> "192.168.0.26"))

  val start = System.currentTimeMillis()

  val receiver = mq.createReceiver()
  val msgs = receiver.receive(20)
  println(msgs.map(_._2))
  receiver.ack(msgs.map(_._1))

  Thread.sleep(5000L)

  receiver.close()

  mq.close()

  println("Done " + (System.currentTimeMillis() - start))
}

object OracleMqTestSend extends App {
  val mq = new OracleMq(Map("host" -> "192.168.0.26"))

  val start = System.currentTimeMillis()

  val sender = mq.createSender()
  for (i <- 1 to 2) {
    sender.send(List("a", "b", "c", "d", "e", "f", "g", "h", "i", "j").map(_ + i))
  }
  sender.close()

  mq.close()

  println("Done " + (System.currentTimeMillis() - start))
}