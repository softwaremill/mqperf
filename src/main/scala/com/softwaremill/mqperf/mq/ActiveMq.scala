package com.softwaremill.mqperf.mq

import javax.jms._
import org.apache.activemq.ActiveMQConnectionFactory

class ActiveMq(val configMap: Map[String, String]) extends JmsMq {

  override lazy val connectionFactory: ConnectionFactory = {
    val cf = new ActiveMQConnectionFactory(configMap("host"))
    cf.setOptimizeAcknowledge(true)
    cf.setSendAcksAsync(true)
    cf
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