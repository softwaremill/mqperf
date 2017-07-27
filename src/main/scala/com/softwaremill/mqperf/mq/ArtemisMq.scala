package com.softwaremill.mqperf.mq

import javax.jms.ConnectionFactory

import com.softwaremill.mqperf.config.TestConfig
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory

class ArtemisMq(val testConfig: TestConfig) extends JmsMq {

  override lazy val connectionFactory: ConnectionFactory = {
    val hosts = "(" + testConfig.brokerHosts.map(h => s"tcp://$h:61616").mkString(",") + ")?ha=true"
    val cf = new ActiveMQConnectionFactory(hosts)
    cf
  }
}
