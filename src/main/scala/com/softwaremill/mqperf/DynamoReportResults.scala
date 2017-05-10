package com.softwaremill.mqperf

import com.amazonaws.services.dynamodbv2.model.{AttributeValue, PutItemRequest}
import com.codahale.metrics._
import com.typesafe.scalalogging.StrictLogging
import org.joda.time.{DateTime, DateTimeZone}

import scala.language.implicitConversions

trait ReportResults {
  def report(metrics: ReceiverMetrics): Unit
}

class DynamoReportResults(nodeId: String, testConfigName: String) extends ReportResults with DynamoResultsTable with StrictLogging {

  override def report(metrics: ReceiverMetrics): Unit = {
    Slf4jReporter.forRegistry(metrics.raw).build().report()
    exportStats(metrics)
  }

  private def exportStats(metrics: ReceiverMetrics): Unit = {

    val timestampStr = timestampFormat.print(metrics.timestamp.withZone(DateTimeZone.UTC))
    val testResultName = s"$testConfigName-$nodeId-${metrics.threadId}"
    val receiveThroughputMeter = metrics.receiveThroughputMeter
    val clusterLatencyTimer = metrics.clusterLatencyTimer.getSnapshot
    val receiveBatchTimer = metrics.receiveBatchTimer.getSnapshot
    logger.info(s"Storing results in DynamoDB: $testResultName")

    dynamoClient.putItem(new PutItemRequest()
      .withTableName(tableName)
      .addItemEntry(resultNameColumn, new AttributeValue(testResultName))
      .addItemEntry(resultTimestampColumn, new AttributeValue(timestampStr))
      .addItemEntry(meterMean, receiveThroughputMeter.getMeanRate)
      .addItemEntry(meter1MinuteEwma, receiveThroughputMeter.getOneMinuteRate)
      .addItemEntry(meter5MinuteEwma, receiveThroughputMeter.getFiveMinuteRate)
      .addItemEntry(meter15MinuteEwma, receiveThroughputMeter.getFifteenMinuteRate)
      .addItemEntry(receiveBatchTimerMinColumn, receiveBatchTimer.getMin)
      .addItemEntry(receiveBatchTimerMaxColumn, receiveBatchTimer.getMax)
      .addItemEntry(receiveBatchTimerMeanColumn, receiveBatchTimer.getMean)
      .addItemEntry(receiveBatchTimerMedianColumn, receiveBatchTimer.getMedian)
      .addItemEntry(receiveBatchTimerStdDevColumn, receiveBatchTimer.getStdDev)
      .addItemEntry(receiveBatchTimer75thPercentileColumn, receiveBatchTimer.get75thPercentile)
      .addItemEntry(receiveBatchTimer95thPercentileColumn, receiveBatchTimer.get95thPercentile)
      .addItemEntry(receiveBatchTimer98thPercentileColumn, receiveBatchTimer.get98thPercentile)
      .addItemEntry(receiveBatchTimer99thPercentileColumn, receiveBatchTimer.get99thPercentile)
      .addItemEntry(clusterTimerMinColumn, clusterLatencyTimer.getMin)
      .addItemEntry(clusterTimerMaxColumn, clusterLatencyTimer.getMax)
      .addItemEntry(clusterTimerMeanColumn, clusterLatencyTimer.getMean)
      .addItemEntry(clusterTimerMedianColumn, clusterLatencyTimer.getMedian)
      .addItemEntry(clusterTimerStdDevColumn, clusterLatencyTimer.getStdDev)
      .addItemEntry(clusterTimer75thPercentileColumn, clusterLatencyTimer.get75thPercentile)
      .addItemEntry(clusterTimer95thPercentileColumn, clusterLatencyTimer.get95thPercentile)
      .addItemEntry(clusterTimer98thPercentileColumn, clusterLatencyTimer.get98thPercentile)
      .addItemEntry(clusterTimer99thPercentileColumn, clusterLatencyTimer.get99thPercentile)
      .addItemEntry(msgsCountColumn, receiveThroughputMeter.getCount))

    logger.info(s"Test results stored: $testResultName")
  }

  implicit def longToDynamoAttr(l: Long): AttributeValue = new AttributeValue().withN(l.toString)
  implicit def doubleToDynamoAttr(d: Double): AttributeValue = new AttributeValue().withN(d.toString)
}

case class ReceiverMetrics(timestamp: DateTime, threadId: Long, receiveThroughputMeter: Meter,
  receiveBatchTimer: Timer, clusterLatencyTimer: Timer, raw: MetricRegistry)

