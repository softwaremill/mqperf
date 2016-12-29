package com.softwaremill.mqperf.stats

import com.softwaremill.mqperf.{DynamoResultsTable, TestMetrics}
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, ComparisonOperator, Condition, ScanRequest}
import org.joda.time.{DateTime, DateTimeZone}

import scala.collection.JavaConverters._

object ShowStats extends App with DynamoResultsTable {

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
      .map(i => {
        Result(
          new DateTime(i.get(resultTimestampColumn).getN.toLong).withZone(DateTimeZone.UTC),
          i.get(msgsCountColumn).getN.toLong,
          i.get(typeColumn).getS,
          i.get(meterMean).getN.toDouble,
          i.get(meter1MinuteEwma).getN.toDouble,
          i.get(timerMinColumn).getN.toLong,
          i.get(timerMaxColumn).getN.toLong,
          i.get(timerMeanColumn).getN.toDouble,
          i.get(timerMedianColumn).getN.toDouble,
          i.get(timerStdDevColumn).getN.toDouble,
          i.get(timer75thPercentileColumn).getN.toDouble,
          i.get(timer95thPercentileColumn).getN.toDouble,
          i.get(timer98thPercentileColumn).getN.toDouble,
          i.get(timer99thPercentileColumn).getN.toDouble
        )
      })
      .toList
  }

  val prefix = if (args.nonEmpty) args(0) else "kafka"

  val allResults = fetchResultsWithPrefix(prefix)
  val (sendResults, receiveResults) = allResults.partition(_._type == TestMetrics.typeSend)

  def printResults(results: List[Result], _type: String) {
    println(s"Results for $prefix, ${_type}")
    results.foreach(println)
    println("---\n")
  }
  printResults(sendResults, "send")
  printResults(receiveResults, "receive")
}
