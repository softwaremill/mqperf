package com.softwaremill.mqperf

class FakeReportResults extends ReportResults {
  override def report(metrics: ReceiverMetrics): Unit = ()
}
