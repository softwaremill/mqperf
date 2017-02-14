package com.softwaremill.mqperf

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, PutItemRequest}
import com.codahale.metrics._
import com.typesafe.scalalogging.StrictLogging
import org.joda.time.{DateTime, DateTimeZone}
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}

import scala.collection.JavaConverters._
import scala.language.implicitConversions


trait ReportResults {
  def report(metrics: ReceiverMetrics): Unit
}

class DynamoReportResults(testConfigName: String) extends ReportResults with DynamoResultsTable with StrictLogging {

  val TimestampFormat: DateTimeFormatter = DateTimeFormat.forPattern("MM-dd'T'HH:mm")

  override def report(metrics: ReceiverMetrics): Unit = {
    Slf4jReporter.forRegistry(metrics.raw).build().report()
    if (dynamoClientOpt.isEmpty) {
      logger.warn("Report requested but Dynamo client not defined.")
    }
    else
      dynamoClientOpt.foreach(exportStats(metrics))
  }

  private def exportStats(metrics: ReceiverMetrics)(dynamoClient: AmazonDynamoDBClient): Unit = {

    val timestampStr = TimestampFormat.print(metrics.timestamp.withZone(DateTimeZone.UTC))
    val testResultName = testConfigName.replace("$runId", timestampStr)
    val meter = metrics.meter
    val clusterTimer = metrics.clusterTimer.getSnapshot
    val timer = metrics.timer.getSnapshot
    logger.info(s"Storing results in DynamoDB: $testResultName")

    dynamoClient.putItem(new PutItemRequest()
      .withTableName(tableName)
      .addItemEntry(resultNameColumn, new AttributeValue(testResultName))
      .addItemEntry(resultTimestampColumn, new AttributeValue(timestampStr))
      .addItemEntry(meterMean, meter.getMeanRate)
      .addItemEntry(meter1MinuteEwma, meter.getOneMinuteRate)
      .addItemEntry(timerMinColumn, timer.getMin)
      .addItemEntry(timerMaxColumn, timer.getMax)
      .addItemEntry(timerMeanColumn, timer.getMean)
      .addItemEntry(timerMedianColumn, timer.getMedian)
      .addItemEntry(timerStdDevColumn, timer.getStdDev)
      .addItemEntry(timer75thPercentileColumn, timer.get75thPercentile)
      .addItemEntry(timer95thPercentileColumn, timer.get95thPercentile)
      .addItemEntry(timer98thPercentileColumn, timer.get98thPercentile)
      .addItemEntry(timer99thPercentileColumn, timer.get99thPercentile)
      .addItemEntry(clusterTimerMinColumn, clusterTimer.getMin)
      .addItemEntry(clusterTimerMaxColumn, clusterTimer.getMax)
      .addItemEntry(clusterTimerMeanColumn, clusterTimer.getMean)
      .addItemEntry(clusterTimerMedianColumn, clusterTimer.getMedian)
      .addItemEntry(clusterTimerStdDevColumn, clusterTimer.getStdDev)
      .addItemEntry(clusterTimer75thPercentileColumn, clusterTimer.get75thPercentile)
      .addItemEntry(clusterTimer95thPercentileColumn, clusterTimer.get95thPercentile)
      .addItemEntry(clusterTimer98thPercentileColumn, clusterTimer.get98thPercentile)
      .addItemEntry(clusterTimer99thPercentileColumn, clusterTimer.get99thPercentile)
      .addItemEntry(msgsCountColumn, new AttributeValue().withN(meter.getCount.toString)))
    logger.info(s"Test results stored: $testResultName")
  }

  implicit def longToDynamoAttr(l: Long): AttributeValue = new AttributeValue().withN(l.toString)

  implicit def doubleToDynamoAttr(d: Double): AttributeValue = new AttributeValue().withN(d.toString)
}

case class ReceiverMetrics(timestamp: DateTime, timer: Timer, meter: Meter, clusterTimer: Timer, raw: MetricRegistry)

object ReceiverMetrics extends StrictLogging {

  val batchThroughputMeter = "receiver-meter"
  val batchLatencyTimerPrefix = "batch-latency-timer"
  val clusterLatencyTimerPrefix = "cluster-latency-timer"

  def apply(metrics: MetricRegistry, timestamp: DateTime): Option[ReceiverMetrics] = {
    val resultOpt = for {
      (_, meter) <- metrics.getMeters.asScala.headOption
      (_, batchTimer) <- metrics.getTimers.asScala.find {
        case (name, _) => name.startsWith(batchLatencyTimerPrefix)
      }
      (_, clusterTimer) <- metrics.getTimers.asScala.find {
        case (name, _) => name.startsWith(clusterLatencyTimerPrefix)
      }
    } yield {
      new ReceiverMetrics(timestamp, batchTimer, meter, clusterTimer, metrics)
    }
    if (resultOpt.isEmpty)
      logger.error(s"Cannot create result object with metrics.\ntimers: ${metrics.getTimers().asScala}\nmeters: ${metrics.getMeters.asScala}")
    resultOpt
  }
}
