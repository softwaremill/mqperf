package mqperf

/** Describes a test run (identified by [[testId]]). The test should run for [[testLengthSeconds]], passing the [[mqConfig]] to the mq
  * implementation.
  *
  * A sender will send up to [[msgsPerProcessInSecond]] messages per process per second. Number of concurrent processes is equal to [[senderConcurrency]].
  * In other words sender should send up to [[senderConcurrency]] * [[msgsPerProcessInSecond]] messages, in batches of [[batchSizeSend]] messages.
  * Each message has [[msgSizeBytes]] bytes.
  *
  * A receiver should receive messages in batches of up to [[batchSizeReceive]]. One receiver receives at most [[receiverConcurrency]]
  * concurrent batches.
  */
case class Config(
    testId: String,
    testLengthSeconds: Int,
    msgSizeBytes: Int,
    batchSizeSend: Int,
    msgsPerProcessInSecond: Int,
    sendersNumbers: Int,
    senderConcurrency: Int,
    batchSizeReceive: Int,
    receiversNumbers: Int,
    receiverConcurrency: Int,
    mqConfig: Map[String, String]
)

