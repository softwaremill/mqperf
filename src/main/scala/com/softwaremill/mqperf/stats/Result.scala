package com.softwaremill.mqperf.stats

import java.util.Locale

import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}

case class Result(
    timestamp: DateTime,
    msgCount: Long,
    _type: String,
    meterMean: Double,
    meter1MinEwma: Double,
    timerMin: Long,
    timerMax: Long,
    timerMean: Double,
    timerMedian: Double,
    timerStdDev: Double,
    timer75thPercentile: Double,
    timer95thPercentile: Double,
    timer98thPercentile: Double,
    timer99thPercentile: Double
) {
  override def toString: String = {
    val dt = Result.dtFormatter.print(timestamp)
    f"""Test time: $dt UTC
       |$msgCount%12d total messages
       |$meterMean%12.0f mean msgs/s
       |$meter1MinEwma%12.0f 1 min EWMA msgs/s
       |${timerMin / 1000000L}%12d min latency ms
       |${timerMax / 1000000L}%12d max latency ms
       |${timerMean / 1000000L}%12.2f mean latency ms
       |${timerMedian / 1000000L}%12.2f median latency ms
       |${timerStdDev / 1000000L}%12.2f std dev latency ms
       |${timer75thPercentile / 1000000L}%12.2f latency ms (75th percentile)
       |${timer95thPercentile / 1000000L}%12.2f latency ms (95th percentile)
       |${timer98thPercentile / 1000000L}%12.2f latency ms (98th percentile)
       |${timer99thPercentile / 1000000L}%12.2f latency ms (99th percentile)""".stripMargin
  }
}

object Result {
  val dtFormatter = DateTimeFormat.shortDateTime().withLocale(Locale.UK).withZone(DateTimeZone.UTC)
}