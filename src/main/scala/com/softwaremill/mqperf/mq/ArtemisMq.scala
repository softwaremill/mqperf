package com.softwaremill.mqperf.mq

import javax.jms.ConnectionFactory

import com.softwaremill.mqperf.config.TestConfig
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory

class ArtemisMq(val testConfig: TestConfig) extends Jms2Mq {

  override lazy val connectionFactory: ConnectionFactory = {
    val hosts = "failover:(" + testConfig.brokerHosts.map(h => s"tcp://$h:61616").mkString(",") + ")"
    val cf = new ActiveMQConnectionFactory(hosts)
    cf.setConfirmationWindowSize(10485760)
    cf.setBlockOnDurableSend(false)
    cf.setBlockOnNonDurableSend(false)
    cf
  }
}
