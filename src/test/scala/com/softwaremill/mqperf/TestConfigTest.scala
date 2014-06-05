package com.softwaremill.mqperf

import org.scalatest.{Matchers, FlatSpec}
import com.softwaremill.mqperf.config.TestConfig

class TestConfigTest extends FlatSpec with Matchers {
  it should "parse an example json" in {
    // given
    val json =
      """
        |{
        |    "name": "sqs1",
        |    "mq_type": "Sqs",
        |    "sender_threads": 10,
        |    "msg_count_per_thread": 100000,
        |    "msg_size": 100,
        |    "max_send_msg_batch_size": 20,
        |    "receiver_threads": 11,
        |    "receive_msg_batch_size": 25
        |}
      """.stripMargin

    // when
    val tc = TestConfig.from(json)

    // then
    tc should be (TestConfig("sqs1", "Sqs", 10, 100000, 100, 20, 11, 25))
  }
}
