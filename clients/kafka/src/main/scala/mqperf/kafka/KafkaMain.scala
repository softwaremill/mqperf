package mqperf.kafka

import mqperf.Server

import java.time.Clock

object KafkaMain extends App {
  Server.start(new KafkaMq(Clock.systemUTC()))
}
