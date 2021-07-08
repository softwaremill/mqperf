package com.softwaremill.mqperf.config

import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}

class TestConfigTest extends FlatSpec with Matchers {
  it should "parse an example json" in {
    // given
    val config = ConfigFactory.parseString {
      """
        |{
        |    "name": "sqs1",
        |    "mq_type": "Sqs",
        |    "sender_threads": 10,
        |    "msg_count_per_thread": 100000,
        |    "msg_size": 100,
        |    "max_send_msg_batch_size": 20,
        |    "receiver_threads": 11,
        |    "receive_msg_batch_size": 25,
        |    "broker_hosts": [ "localhost1", "localhost2" ]
        |}
      """.stripMargin
    }

    // when
    val tc = TestConfig.from("y", config)

    // then
    tc should be(TestConfig("sqs1", "Sqs", 10, 100000, 100, 20, 11, 25, List("localhost1", "localhost2"), "y", ConfigFactory.empty()))
  }

  it should "parse an example json with mq config map" in {
    // given
    val config = ConfigFactory.parseString {
      """
        |{
        |    "name": "sqs1",
        |    "mq_type": "Sqs",
        |    "sender_threads": 10,
        |    "msg_count_per_thread": 100000,
        |    "msg_size": 100,
        |    "max_send_msg_batch_size": 20,
        |    "receiver_threads": 11,
        |    "receive_msg_batch_size": 25,
        |    "broker_hosts": [],
        |    "mq": {
        |       "f1": "v1",
        |       "f2": 10
        |    }
        |}
      """.stripMargin
    }

    // when
    val tc = TestConfig.from("y", config)

    // then
    tc should be(TestConfig("sqs1", "Sqs", 10, 100000, 100, 20, 11, 25, Nil, "y", config.getConfig("mq")))
  }

  for {
    (hostPortString, (host, port)) <- List(
      "localhost" -> ("localhost", None),
      "127.0.0.1" -> ("127.0.0.1", None),
      "localhost:12345" -> ("localhost", Some(12345)),
      "127.0.0.1:12345" -> ("127.0.0.1", Some(12345))
    )
  } it should s"parse $host${port.map(':' + _).getOrElse("")} from $hostPortString" in {
    TestConfig.parseHostPort(hostPortString) should be((host, port))
  }

}
