package com.softwaremill.mqperf.stats

import java.util.Locale

import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import org.joda.time.{DateTime, DateTimeZone}

case class TimerResult(
    name: String,
    min: Long,
    max: Long,
    mean: Double,
    median: Double,
    stdDev: Double,
    p75th: Double,
    p95th: Double,
    p98th: Double,
    p99th: Double
) {
  override def toString: String = {
    f"""Timer ($name)
  |${min / 1000000L}%12d min ms
  |${max / 1000000L}%12d max ms
  |${mean / 1000000L}%12.2f mean ms
  |${median / 1000000L}%12.2f median ms
  |${stdDev / 1000000L}%12.2f std dev ms
  |${p75th / 1000000L}%12.2f ms (75th percentile)
  |${p95th / 1000000L}%12.2f ms (95th percentile)
  |${p98th / 1000000L}%12.2f ms (98th percentile)
  |${p99th / 1000000L}%12.2f ms (99th percentile)""".stripMargin
  }
}
case class Result(
    timestamp: DateTime,
    msgCount: Long,
    meterMean: Double,
    meter1MinEwma: Double,
    meter5MinEwma: Double,
    meter15MinEwma: Double,
    timer: TimerResult,
    clusterTimer: TimerResult
) {
  override def toString: String = {
    val dt = Result.DtFormatter.print(timestamp)
    f"""Test time: $dt UTC
       |$msgCount%12d total messages
       |$meterMean%12.0f mean msgs/s
       |$meter1MinEwma%12.0f 1 min EWMA msgs/s
       |$meter5MinEwma%12.0f 5 min EWMA msgs/s
       |$meter15MinEwma%12.0f 15 min EWMA msgs/s
       |$timer
       |$clusterTimer""".stripMargin
  }
}

object Result {
  val DtFormatter: DateTimeFormatter = DateTimeFormat.shortDateTime().withLocale(Locale.UK).withZone(DateTimeZone.UTC)
}