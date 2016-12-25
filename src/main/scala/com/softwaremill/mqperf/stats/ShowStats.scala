package com.softwaremill.mqperf.stats

import com.softwaremill.mqperf.{DynamoResultsTable, TestMetrics}
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, ComparisonOperator, Condition, ScanRequest}

import scala.collection.JavaConverters._

object ShowStats extends App with DynamoResultsTable {

  case class Result(
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
  )

  private def fetchResultsWithPrefix(prefix: String): List[Result] = {
    val condition = new Condition()
      .withComparisonOperator(ComparisonOperator.BEGINS_WITH)
      .withAttributeValueList(new AttributeValue(prefix))

    def doFetch(lastEvaluatedKey: Option[java.util.Map[String, AttributeValue]]): java.util.List[java.util.Map[String, AttributeValue]] =
      (for {
        dynamoClient <- dynamoClientOpt
      } yield {
        val req1 = new ScanRequest(tableName).addScanFilterEntry(resultNameColumn, condition)
        val req2 = lastEvaluatedKey.map(req1.withExclusiveStartKey).getOrElse(req1)

        val result = dynamoClient.scan(req2)

        val fetchedItems: java.util.List[java.util.Map[String, AttributeValue]] = result.getItems
        val newLastEvaluatedKey = result.getLastEvaluatedKey

        if (newLastEvaluatedKey != null && newLastEvaluatedKey.size() > 0) {
          fetchedItems.addAll(doFetch(Some(newLastEvaluatedKey)))
        }

        fetchedItems
      }).getOrElse(Nil.asJava)

    val items = doFetch(None).asScala

    items
      .map(i => Result(
        i.get(msgsCountColumn).getN.toLong,
        i.get(typeColumn).getS,
        i.get(histogramMinColumn).getN.toLong,
        i.get(histogramMaxColumn).getN.toLong,
        i.get(histogramMeanColumn).getN.toDouble,
        i.get(histogramMedianColumn).getN.toDouble,
        i.get(histogramStdDevColumn).getN.toDouble,
        i.get(histogram75thPercentileColumn).getN.toDouble,
        i.get(histogram95thPercentileColumn).getN.toDouble,
        i.get(histogram98thPercentileColumn).getN.toDouble,
        i.get(histogram99thPercentileColumn).getN.toDouble,
        i.get(timerMinColumn).getN.toLong,
        i.get(timerMaxColumn).getN.toLong,
        i.get(timerMeanColumn).getN.toDouble,
        i.get(timerMedianColumn).getN.toDouble,
        i.get(timerStdDevColumn).getN.toDouble,
        i.get(timer75thPercentileColumn).getN.toDouble,
        i.get(timer95thPercentileColumn).getN.toDouble,
        i.get(timer98thPercentileColumn).getN.toDouble,
        i.get(timer99thPercentileColumn).getN.toDouble
      ))
      .toList
  }

  val prefix = if (args.nonEmpty) args(0) else "kafka"

  val allResults = fetchResultsWithPrefix(prefix)
  val (sendResults, receiveResults) = allResults.partition(_._type == TestMetrics.typeSend)

  def printResults(results: List[Result], _type: String) {
    println(s"Results for $prefix, ${_type}")
    results.foreach { r =>
      println(r) // TODO nice formatting
    }
    val totalMsgs = results.map(_.msgCount).sum
    println("---\n")
  }

  printResults(sendResults, "send")
  printResults(receiveResults, "receive")
}
