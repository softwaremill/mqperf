package mqperf

/** Describes a test run (identified by [[testId]]). The test should run for [[testLengthSeconds]], passing the [[mqConfig]] to the mq
  * implementation.
  *
  * A sender should send up to [[msgsPerSecond]] messages, in batches of [[batchSizeSend]] messages, where each message has [[msgSizeBytes]]
  * bytes. One sender sends at most [[senderConcurrency]] concurrent batches.
  *
  * A receiver should receive messages in batches of up to [[batchSizeReceive]]
  */
case class Config(
    testId: String,
    testLengthSeconds: Int,
    msgsPerSecond: Int,
    msgSizeBytes: Int,
    batchSizeSend: Int,
    batchSizeReceive: Int,
    senderConcurrency: Int,
    mqConfig: Map[String, String]
)

