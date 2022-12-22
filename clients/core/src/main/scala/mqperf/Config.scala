package mqperf

/** Describes a test run (identified by [[testId]]). The test should run for [[testLengthSeconds]], passing the [[mqConfig]] to the mq
  * implementation.
  *
  * A sender should send up to [[msgsPerSecond]] messages, in batches of [[batchSizeSend]] messages, where each message has [[msgSizeBytes]]
  * bytes. One sender sends at most [[senderConcurrency]] concurrent batches.
  *
  * A receiver should receive messages in batches of up to [[batchSizeReceive]]. One receiver receives at most [[receiverConcurrency]]
  * concurrent batches.
  */
case class Config(
    testId: String,
    testLengthSeconds: Int,
    msgsPerSecond: Int,
    msgSizeBytes: Int,
    batchSizeSend: Int,
    senderConcurrency: Int,
    batchSizeReceive: Int,
    receiverConcurrency: Int,
    mqConfig: Map[String, String]
)

