package com.softwaremill.mqperf

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, PutItemRequest}
import com.codahale.metrics._
import com.fasterxml.uuid.{EthernetAddress, Generators}
import com.softwaremill.mqperf.DynamoReportResults._
import com.typesafe.scalalogging.StrictLogging
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import org.joda.time.{DateTime, DateTimeZone}

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
    val testResultName = RunIdPattern.replaceFirstIn(testConfigName, timestampStr) + s"-$NodeId-${metrics.threadId}"
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

object DynamoReportResults {
  val RunIdPattern = """(?i)\$runid""".r

  val NodeId = Generators.timeBasedGenerator(EthernetAddress.fromInterface()).generate().node()
}

case class ReceiverMetrics(timestamp: DateTime, threadId: Long, timer: Timer, meter: Meter, clusterTimer: Timer, raw: MetricRegistry)

object ReceiverMetrics extends StrictLogging {

  val receiveThroughputMeterPrefix = "receiver-throughput-meter"
  val receiveBatchTimerPrefix = "receive-batch-timer"
  val clusterLatencyTimerPrefix = "cluster-latency-timer"

  def apply(metrics: MetricRegistry, timestamp: DateTime, threadId: Long): Option[ReceiverMetrics] = {
    val resultOpt = for {
      (_, meter) <- metrics.getMeters.asScala.headOption
      (_, batchTimer) <- metrics.getTimers.asScala.find {
        case (name, _) => name.startsWith(receiveBatchTimerPrefix)
      }
      (_, clusterTimer) <- metrics.getTimers.asScala.find {
        case (name, _) => name.startsWith(clusterLatencyTimerPrefix)
      }
    } yield {
      new ReceiverMetrics(timestamp, threadId, batchTimer, meter, clusterTimer, metrics)
    }
    if (resultOpt.isEmpty)
      logger.error(s"Cannot create result object with metrics.\ntimers: ${metrics.getTimers().asScala}\nmeters: ${metrics.getMeters.asScala}")
    resultOpt
  }
}
