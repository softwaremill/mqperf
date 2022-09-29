package mqperf

/** Describes a test run (identified by [[testId]]). The test should run for [[testLengthSeconds]], passing the [[mqConfig]] to the mq
  * implementation.
  *
  * A sender should send up to [[msgsPerSecond]] messages, in batches of [[batchSizeSend]] messages, where each message has [[msgSizeBytes]]
  * bytes. At most [[maxSendInFlight]] messages should be in flight (sent initiated, but not yet complete) at any given time.
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
    maxSendInFlight: Int,
    mqConfig: Map[String, String]
)

/*
curl -XPOST -d'{"testId":"test","testLengthSeconds":10,"msgsPerSecond":10,"msgSizeBytes":100,"batchSizeSend":1,"batchSizeReceive":1,"maxSendInFlight":1,"mqConfig":{"hosts":"broker:29092","topic":"mqperf-test","acks":"-1","groupId":"mqperf","commitMs":"1000","partitions":"10","replicationFactor":"1"}}' http://localhost:8080/init
 */
