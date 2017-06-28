package com.softwaremill.mqperf.mq

import javax.jms._

import com.softwaremill.mqperf.config.TestConfig
import org.apache.activemq.ActiveMQConnectionFactory

class ActiveMq(val testConfig: TestConfig) extends JmsMq {

  override lazy val connectionFactory: ConnectionFactory = {
    val hosts = "failover:(" + testConfig.brokerHosts.map(h => s"tcp://$h:61616").mkString(",") + ")"
    val cf = new ActiveMQConnectionFactory(hosts)
    cf.setOptimizeAcknowledge(true)
    cf.setSendAcksAsync(true)
    cf
  }
}
