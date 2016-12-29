package com.softwaremill.mqperf

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, PutItemRequest}
import com.codahale.metrics._
import com.typesafe.scalalogging.StrictLogging
import org.joda.time.DateTime

import scala.collection.JavaConverters._
import scala.util.Random

class ReportResults(testConfigName: String) extends DynamoResultsTable with StrictLogging {

  def report(metrics: TestMetrics): Unit = {
    Slf4jReporter.forRegistry(metrics.raw).build().report()
    if (dynamoClientOpt.isEmpty) {
      logger.warn("Report requested but Dynamo client not defined.")
    }
    else
      dynamoClientOpt.foreach(exportStats(metrics))
  }

  private def exportStats(metrics: TestMetrics)(dynamoClient: AmazonDynamoDBClient): Unit = {

    val testResultName = s"$testConfigName-${metrics.name}-${Random.nextInt(100000)}"
    val meter = metrics.meter
    val timer = metrics.timer.getSnapshot
    logger.info(s"Storing results in DynamoDB: $testResultName")

    dynamoClient.putItem(new PutItemRequest()
      .withTableName(tableName)
      .addItemEntry(resultNameColumn, new AttributeValue(testResultName))
      .addItemEntry(resultTimestampColumn, metrics.timestamp.getMillis)
      .addItemEntry(typeColumn, new AttributeValue(metrics.name))
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
      .addItemEntry(msgsCountColumn, new AttributeValue().withN(meter.getCount.toString)))
    logger.info(s"Test results stored: $testResultName")
  }

  implicit def longToDynamoAttr(l: Long): AttributeValue = new AttributeValue().withN(l.toString)

  implicit def doubleToDynamoAttr(d: Double): AttributeValue = new AttributeValue().withN(d.toString)
}

case class TestMetrics(name: String, timestamp: DateTime, timer: Timer, meter: Meter, raw: MetricRegistry)

object TestMetrics extends StrictLogging {

  val typeSend = "s"
  val typeReceive = "r"

  def receive(metrics: MetricRegistry): Option[TestMetrics] = apply(typeReceive, metrics)

  def send(metrics: MetricRegistry): Option[TestMetrics] = apply(typeSend, metrics)

  def apply(name: String, metrics: MetricRegistry): Option[TestMetrics] = {
    val resultOpt = for {
      (_, timer) <- metrics.getTimers.asScala.headOption
      (_, meter) <- metrics.getMeters.asScala.headOption
    } yield {
      new TestMetrics(name, DateTime.now(), timer, meter, metrics)
    }
    if (resultOpt.isEmpty)
      logger.error("Cannot create result object with metrics.")
    resultOpt
  }
}
