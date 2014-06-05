package com.softwaremill.mqperf.stats

import com.softwaremill.mqperf.DynamoResultsTable
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, ComparisonOperator, Condition, ScanRequest}
import scala.collection.JavaConversions._

object ShowStats extends App with DynamoResultsTable {
  private def fetchItemsWithPrefix(prefix: String) {
    val items = dynamoClient
      .scan(
        new ScanRequest(tableName).addScanFilterEntry(resultNameColumn,
          new Condition()
            .withComparisonOperator(ComparisonOperator.BEGINS_WITH)
            .withAttributeValueList(new AttributeValue(prefix))))
      .getItems

    items.foreach(println)
  }

  fetchItemsWithPrefix("1401965734540-sqs1-s") //1401965734540-sqs1-s-372
}
