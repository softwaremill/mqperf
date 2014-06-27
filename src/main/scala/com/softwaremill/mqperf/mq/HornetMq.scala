package com.softwaremill.mqperf.mq

import org.hornetq.api.core.TransportConfiguration
import org.hornetq.api.core.client._
import org.hornetq.core.remoting.impl.netty.{NettyConnectorFactory, TransportConstants}

import scala.annotation.tailrec

/**
 * Config changes:
 * hornetq-configuration.xml:
 * - <security-enabled>false</security-enabled>
 */
class HornetMq(configMap: Map[String, String]) extends Mq {

  private val QueueName = "mq"
  private val ContentPropertyName = "v"

  val serverLocator = {
    val nettyParams = new java.util.HashMap[String, Object]()
    nettyParams.put(TransportConstants.HOST_PROP_NAME, configMap("host"))
    nettyParams.put(TransportConstants.PORT_PROP_NAME, configMap("port"))

    val sl = HornetQClient.createServerLocatorWithHA(
      new TransportConfiguration(classOf[NettyConnectorFactory].getName, nettyParams))

    sl.setConfirmationWindowSize(1048576)

    sl.setBlockOnAcknowledge(false)

    sl
  }

  def createSession(sf: ClientSessionFactory) = sf.createSession(false, true, 0)

  val sf = {
    val sf = serverLocator.createSessionFactory()

    val session = createSession(sf)
    try {
      session.createQueue(QueueName, QueueName, true)
    } catch {
      case e: Exception if e.getMessage.contains("HQ119019") => // queue already exists
    }

    session.close()
    sf
  }

  type MsgId = ClientMessage

  override def createSender() = new MqSender {
    val session = createSession(sf)
    val producer = session.createProducer(QueueName)

    override def send(msgs: List[String]) {
      for (rawMsg <- msgs) {
        val msg = session.createMessage(true)
        msg.putStringProperty(ContentPropertyName, rawMsg)
        producer.send(msg)
      }

      session.commit()
    }

    override def close() {
      session.close()
    }
  }

  override def createReceiver() = new MqReceiver {
    val session = createSession(sf)
    val consumer = session.createConsumer(QueueName)
    session.start()

    override def receive(maxMsgCount: Int) = {
      doReceive(Nil, waitForMsgs = true, maxMsgCount)
    }

    @tailrec
    private def doReceive(acc: List[(MsgId, String)], waitForMsgs: Boolean, count: Int): List[(MsgId, String)] = {
      if (count == 0) {
        acc
      } else {
        val msg = if (waitForMsgs) consumer.receive(1000L) else consumer.receive(0L)
        if (msg == null) {
          acc
        } else {
          doReceive((msg, msg.getStringProperty(ContentPropertyName)) :: acc, waitForMsgs = false, count-1)
        }
      }
    }

    override def ack(ids: List[MsgId]) {
      ids.foreach(_.acknowledge())
    }

    override def close() {
      session.close()
    }
  }

  override def close() {
    sf.close()
  }
}

object X1 extends App {
  val cfg = Map("host" -> "localhost", "port" -> "5445")
//  val cfg = Map("host" -> "ec2-54-220-200-183.eu-west-1.compute.amazonaws.com", "port" -> "5445")
  val mq = new HornetMq(cfg)
  val sender = mq.createSender()
  val start = System.currentTimeMillis()
  for (j <- 1 to 10) {
    sender.send((for (i <- 1 to 10) yield "m" + i).toList)
    val end = System.currentTimeMillis()
    println("I", end-start)
  }
  val end = System.currentTimeMillis()
  println((end-start)/1000L)
  //sender.send(List("a", "b", "c", "d", "e", "f", "g", "h"))
  sender.close()
  mq.close()
}

object X2 extends App {
  val cfg = Map("host" -> "localhost", "port" -> "5445")
  //  val cfg = Map("host" -> "ec2-54-220-200-183.eu-west-1.compute.amazonaws.com", "port" -> "5445")
  val mq = new HornetMq(cfg)
  val receiver = mq.createReceiver()
  val start = System.currentTimeMillis()
  for (j <- 1 to 10) {
    val msgs = receiver.receive(1)
    println(msgs.map(_._2))
    receiver.ack(msgs.map(_._1))
    val end = System.currentTimeMillis()
    println("I", end-start)
  }
  val end = System.currentTimeMillis()
  println((end-start)/1000L)
  receiver.close()
  mq.close()
}
