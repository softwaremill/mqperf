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
    val hSnapshot = metrics.histogram.getSnapshot
    logger.info(s"Storing results in DynamoDB: $testResultName")

    dynamoClient.putItem(new PutItemRequest()
      .withTableName(tableName)
      .addItemEntry(resultNameColumn, new AttributeValue(testResultName))
      .addItemEntry(msgsCountColumn, new AttributeValue().withN(hSnapshot.size.toString)))
    // TODO export all stats
    logger.info(s"Test results stored: $testResultName")
  }
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
