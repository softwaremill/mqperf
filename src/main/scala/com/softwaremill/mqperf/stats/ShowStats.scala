package com.softwaremill.mqperf.stats

import com.softwaremill.mqperf.DynamoResultsTable
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, ComparisonOperator, Condition, ScanRequest}
import scala.collection.JavaConverters._

object ShowStats extends App with DynamoResultsTable {

  case class Result(start: String, end: String, took: Long, msgsCount: Int, _type: String) {
    val msgsPerSecond: Double = msgsCount.toDouble / took * 1000
  }

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
        i.get(startColumn).getS,
        i.get(endColumn).getS,
        i.get(tookColumn).getN.toLong,
        i.get(msgsCountColumn).getN.toInt,
        i.get(typeColumn).getS
      ))
      .toList
  }

  val prefix = if (args.nonEmpty) args(0) else "sqs1-1401970126140"

  val allResults = fetchResultsWithPrefix(prefix).sortBy(-_.msgsPerSecond)
  val (sendResults, receiveResults) = allResults.partition(_._type == typeSend)

  def printResults(results: List[Result], _type: String) {
    println(s"Results for $prefix, ${_type}")
    results.foreach { r =>
      println("%05.2f m/s: %dms, %d messages (%s -> %s)".format(r.msgsPerSecond, r.took, r.msgsCount, r.start, r.end))
    }
    val totalMsgs = results.map(_.msgsCount).sum
    val avg = totalMsgs.toDouble / results.map(_.took).sum * 1000
    println("Average: %05.2f msgs/second (%d msgs)".format(avg, totalMsgs))

    println("---\n")
  }

  printResults(sendResults, "send")
  printResults(receiveResults, "receive")
}
