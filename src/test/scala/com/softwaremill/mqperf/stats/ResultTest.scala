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
      3250346112L, 21217.55, 20217.55, 20219.56, 20211.57, TimerResult(
        "latency", 7211L, 23301401L, 10627650.458852611, 9463284, 8410536.830270134, 23301401,
        23301301, 23201401, 23101401
      ),
      TimerResult(
        "cluster latency", 6211L, 13301401L, 5627650.458852611, 8463284, 7410536.830270134, 13301401,
        13301301, 13201401, 13101401
      )
    ).toString

    Locale.setDefault(defaultLocale)

    resultAsPrettyString should be(
      """Test time: 29/12/16 13:45 UTC
        |  3250346112 total messages
        |       21218 mean msgs/s
        |       20218 1 min EWMA msgs/s
        |       20220 5 min EWMA msgs/s
        |       20212 15 min EWMA msgs/s
        |Timer (latency)
        |           0 min ms
        |          23 max ms
        |       10.63 mean ms
        |        9.46 median ms
        |        8.41 std dev ms
        |       23.30 ms (75th percentile)
        |       23.30 ms (95th percentile)
        |       23.20 ms (98th percentile)
        |       23.10 ms (99th percentile)
        |Timer (cluster latency)
        |           0 min ms
        |          13 max ms
        |        5.63 mean ms
        |        8.46 median ms
        |        7.41 std dev ms
        |       13.30 ms (75th percentile)
        |       13.30 ms (95th percentile)
        |       13.20 ms (98th percentile)
        |       13.10 ms (99th percentile)""".stripMargin
    )
  }
}
