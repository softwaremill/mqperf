package com.softwaremill.mqperf.stats

private[stats] case class Result(
    msgCount: Long,
    _type: String,
    histogramMin: Long,
    histogramMax: Long,
    histogramMean: Double,
    histogramMedian: Double,
    histogramStdDev: Double,
    histogram75thPercentile: Double,
    histogram95thPercentile: Double,
    histogram98thPercentile: Double,
    histogram99thPercentile: Double,
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
    f"""$msgCount%12d total messages
       |$histogramMin%12d min msgs/s
       |$histogramMax%12d max msgs/s
       |$histogramMean%12.2f mean msgs/s
       |$histogramMedian%12.2f median msgs/s
       |$histogramStdDev%12.2f std dev msgs/s
       |$histogram75thPercentile%12.2f msgs/s (75th percentile)
       |$histogram95thPercentile%12.2f msgs/s (95th percentile)
       |$histogram98thPercentile%12.2f msgs/s (98th percentile)
       |$histogram99thPercentile%12.2f msgs/s (99th percentile)
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
