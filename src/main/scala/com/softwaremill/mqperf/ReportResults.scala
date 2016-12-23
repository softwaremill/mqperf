package com.softwaremill.mqperf

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, PutItemRequest}
import com.codahale.metrics._
import com.typesafe.scalalogging.StrictLogging
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
    val histogram = metrics.histogram.getSnapshot
    val timer = metrics.timer.getSnapshot
    logger.info(s"Storing results in DynamoDB: $testResultName")

    dynamoClient.putItem(new PutItemRequest()
      .withTableName(tableName)
      .addItemEntry(resultNameColumn, new AttributeValue(testResultName))
      .addItemEntry(typeColumn, new AttributeValue(metrics.name))
      .addItemEntry(histogramMinColumn, histogram.getMin)
      .addItemEntry(histogramMaxColumn, histogram.getMax)
      .addItemEntry(histogramMeanColumn, histogram.getMean)
      .addItemEntry(histogramMedianColumn, histogram.getMedian)
      .addItemEntry(histogramStdDevColumn, histogram.getStdDev)
      .addItemEntry(histogram75thPercentileColumn, histogram.get75thPercentile)
      .addItemEntry(histogram95thPercentileColumn, histogram.get95thPercentile)
      .addItemEntry(histogram98thPercentileColumn, histogram.get98thPercentile)
      .addItemEntry(histogram99thPercentileColumn, histogram.get99thPercentile)
      .addItemEntry(timerMinColumn, timer.getMin)
      .addItemEntry(timerMaxColumn, timer.getMax)
      .addItemEntry(timerMeanColumn, timer.getMean)
      .addItemEntry(timerMedianColumn, timer.getMedian)
      .addItemEntry(timerStdDevColumn, timer.getStdDev)
      .addItemEntry(timer75thPercentileColumn, timer.get75thPercentile)
      .addItemEntry(timer95thPercentileColumn, timer.get95thPercentile)
      .addItemEntry(timer98thPercentileColumn, timer.get98thPercentile)
      .addItemEntry(timer99thPercentileColumn, timer.get99thPercentile)
      .addItemEntry(msgsCountColumn, new AttributeValue().withN(histogram.size.toString)))
    logger.info(s"Test results stored: $testResultName")
  }

  implicit def longToDynamoAttr(l: Long): AttributeValue = new AttributeValue().withN(l.toString)

  implicit def doubleToDynamoAttr(d: Double): AttributeValue = new AttributeValue().withN(d.toString)
}

case class TestMetrics(name: String, timer: Timer, histogram: Histogram, raw: MetricRegistry)

object TestMetrics extends StrictLogging {

  val typeSend = "s"
  val typeReceive = "r"

  def receive(metrics: MetricRegistry): Option[TestMetrics] = apply(typeReceive, metrics)

  def send(metrics: MetricRegistry): Option[TestMetrics] = apply(typeSend, metrics)

  def apply(name: String, metrics: MetricRegistry): Option[TestMetrics] = {
    val resultOpt = for {
      (_, timer) <- metrics.getTimers.asScala.headOption
      (_, histogram) <- metrics.getHistograms.asScala.headOption
    } yield {
      new TestMetrics(name, timer, histogram, metrics)
    }
    if (resultOpt.isEmpty)
      logger.error("Cannot create result object with metrics.")
    resultOpt
  }
}
