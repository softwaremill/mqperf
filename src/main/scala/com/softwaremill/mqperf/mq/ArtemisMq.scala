package com.softwaremill.mqperf.mq

import java.util

import com.softwaremill.mqperf.config.TestConfig
import org.apache.activemq.artemis.api.core.TransportConfiguration
import org.apache.activemq.artemis.api.jms.{ActiveMQJMSClient, JMSFactoryType}
import org.apache.activemq.artemis.core.remoting.impl.netty.{NettyConnectorFactory, TransportConstants}

class ArtemisMq(testConfig: TestConfig) extends JmsMq {

  override lazy val connectionFactory = {
    val host = testConfig.mqConfig.getString("host")
    val connectionParams = new util.HashMap[String, Object]()
    connectionParams.put(TransportConstants.HOST_PROP_NAME, host)
    val transportConfiguration = new TransportConfiguration(classOf[NettyConnectorFactory].getName, connectionParams)
    val cf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, transportConfiguration)
    cf.setBlockOnAcknowledge(false)
    cf
  }
}
