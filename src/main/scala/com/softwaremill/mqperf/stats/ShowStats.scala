package com.softwaremill.mqperf.stats

import com.amazonaws.services.dynamodbv2.model.{AttributeValue, ComparisonOperator, Condition, ScanRequest}
import com.softwaremill.mqperf.{DynamoResultsTable, ReceiverMetrics}
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
          i.get(meterMean).getN.toDouble,
          i.get(meter1MinuteEwma).getN.toDouble,
          TimerResult(
            ReceiverMetrics.batchLatencyTimerPrefix,
            i.get(timerMinColumn).getN.toLong,
            i.get(timerMaxColumn).getN.toLong,
            i.get(timerMeanColumn).getN.toDouble,
            i.get(timerMedianColumn).getN.toDouble,
            i.get(timerStdDevColumn).getN.toDouble,
            i.get(timer75thPercentileColumn).getN.toDouble,
            i.get(timer95thPercentileColumn).getN.toDouble,
            i.get(timer98thPercentileColumn).getN.toDouble,
            i.get(timer99thPercentileColumn).getN.toDouble
          ),
          TimerResult(
            ReceiverMetrics.clusterLatencyTimerPrefix,
            i.get(clusterTimerMinColumn).getN.toLong,
            i.get(clusterTimerMaxColumn).getN.toLong,
            i.get(clusterTimerMeanColumn).getN.toDouble,
            i.get(clusterTimerMedianColumn).getN.toDouble,
            i.get(clusterTimerStdDevColumn).getN.toDouble,
            i.get(clusterTimer75thPercentileColumn).getN.toDouble,
            i.get(clusterTimer95thPercentileColumn).getN.toDouble,
            i.get(clusterTimer98thPercentileColumn).getN.toDouble,
            i.get(clusterTimer99thPercentileColumn).getN.toDouble
          )
        )
      })
      .toList
  }

  val prefix = if (args.nonEmpty) args(0) else "kafka"

  val allResults = fetchResultsWithPrefix(prefix)

  def printResults(results: List[Result], _type: String) {
    println(s"Results for $prefix, ${_type}")
    results.foreach(println)
    println("---\n")
  }
  printResults(allResults, "send")
}
