package com.softwaremill.mqperf.mq

import javax.jms._

import com.softwaremill.mqperf.config.TestConfig
import org.apache.activemq.ActiveMQConnectionFactory

class ActiveMq(testConfig: TestConfig) extends JmsMq {

  override lazy val connectionFactory: ConnectionFactory = {
    val cf = new ActiveMQConnectionFactory(testConfig.mqConfig.getString("host"))
    cf.setOptimizeAcknowledge(true)
    cf.setSendAcksAsync(true)
    cf
  }
}
