package com.softwaremill.mqperf.stats

import java.util.Locale

import org.joda.time.DateTime
import org.scalatest.{FlatSpec, Matchers}

class ResultTest extends FlatSpec with Matchers {

  behavior of "Result"

  it should "be correctly represented as string" in {
    val defaultLocale = Locale.getDefault
    Locale.setDefault(Locale.US)
    val resultAsPrettyString = Result(
      new DateTime(1483019133000L),
      3250346112L, "r", 21217.55, 21223.32, 7211L, 23301401L, 10627650.458852611, 9463284, 8410536.830270134, 23301401,
      23301301, 23201401, 23101401
    ).toString

    Locale.setDefault(defaultLocale)

    resultAsPrettyString should be(
      """Test time: 29/12/16 13:45 UTC
        |  3250346112 total messages
        |       21218 mean msgs/s
        |       21223 1 min EWMA msgs/s
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
