package com.softwaremill.mqperf.mq

import java.util
import org.apache.activemq.artemis.api.core.TransportConfiguration
import org.apache.activemq.artemis.api.jms.{ActiveMQJMSClient, JMSFactoryType}
import org.apache.activemq.artemis.core.remoting.impl.netty.{NettyConnectorFactory, TransportConstants}

class ArtemisMq(val configMap: Map[String, String]) extends JmsMq {

  override lazy val connectionFactory = {
    val host = configMap("host")
    val connectionParams = new util.HashMap[String, Object]()
    connectionParams.put(TransportConstants.HOST_PROP_NAME, host)
    val transportConfiguration = new TransportConfiguration(classOf[NettyConnectorFactory].getName, connectionParams)
    val cf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, transportConfiguration)
    cf.setBlockOnAcknowledge(false)
    cf
  }
}

object ArtemisMqTestSend extends App {
  val mq = new ArtemisMq(Map("host" -> "localhost"))

  val start = System.currentTimeMillis()

  val sender = mq.createSender()
  for (i <- 1 to 100) {
    sender.send(List("a", "b", "c", "d", "e", "f", "g", "h", "i", "j").map(_ + i))
  }
  sender.close()

  mq.close()

  println("Done " + (System.currentTimeMillis() - start))
}

object ArtemisMqTestReceive extends App {
  val mq = new ArtemisMq(Map("host" -> "localhost"))

  val receiver = mq.createReceiver()
  val msgs = receiver.receive(2000)
  println(msgs.map(_._2))
  receiver.ack(msgs.map(_._1))

  Thread.sleep(5000L)

  receiver.close()

  mq.close()
}
