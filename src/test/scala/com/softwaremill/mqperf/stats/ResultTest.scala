package com.softwaremill.mqperf.stats

import org.scalatest.{FlatSpec, Matchers}

class ResultTest extends FlatSpec with Matchers {

  behavior of "Result"

  it should "be correctly represented as string" in {
    Result(
      3250346112L, "r", 21217, 21223, 21219.601627457203, 21221, 2.6071720196218555, 21223, 21224,
      21224.7, 21224, 7211L, 23301401L, 10627650.458852611, 9463284, 8410536.830270134, 23301401,
      23301301, 23201401, 23101401
    ).toString should be(
        """  3250346112 total messages
        |       21217 min msgs/s
        |       21223 max msgs/s
        |    21219.60 mean msgs/s
        |    21221.00 median msgs/s
        |        2.61 std dev msgs/s
        |    21223.00 msgs/s (75th percentile)
        |    21224.00 msgs/s (95th percentile)
        |    21224.70 msgs/s (98th percentile)
        |    21224.00 msgs/s (99th percentile)
        |           0 min latency ms
        |          23 max latency ms
        |       10.63 mean latency ms
        |        9.46 median latency ms
        |        8.41 std dev latency ms
        |       23.30 latency ms (75th percentile)
        |       23.30 latency ms (95th percentile)
        |       23.20 latency ms (98th percentile)
        |       23.10 latency ms (99th percentile)""".stripMargin
      )
  }
}
