package mqperf

import scala.concurrent.duration.Duration

/** Describes a test run (identified by [[testId]]). The test should run for [[testLength]], passing the [[mqConfig]] to the mq
  * implementation.
  *
  * A sender should send up to [[msgsPerSecond]] messages, in batches of [[batchSizeSend]] messages, where each message has [[msgSize]]
  * bytes. At most [[maxSendInFlight]] messages should be in flight (sent initiated, but not yet complete) at any given time.
  *
  * A receiver should receive messages in batches of up to [[batchSizeReceive]]
  */
case class Config(
    testId: String,
    testLength: Duration,
    msgsPerSecond: Int,
    msgSize: Int,
    batchSizeSend: Int,
    batchSizeReceive: Int,
    maxSendInFlight: Int,
    mqConfig: Map[String, String]
)
