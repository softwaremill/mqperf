package com.softwaremill.mqperf

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.softwaremill.mqperf.config.AWSCredentialsFromEnv
import com.amazonaws.regions.{Regions, Region}
import com.amazonaws.services.dynamodbv2.model.{DeleteItemRequest, ScanRequest}
import scala.collection.JavaConversions._

trait DynamoResultsTable {
  protected val dynamoClient = {
    val c = new AmazonDynamoDBClient(AWSCredentialsFromEnv())
    c.setRegion(Region.getRegion(Regions.EU_WEST_1))
    c
  }
  protected val tableName = "mqperf-results"

  protected val resultNameColumn = "test_result_name"
  protected val msgsCountColumn = "msgs_count"
  protected val tookColumn = "took"
  protected val startColumn = "start"
  protected val endColumn = "end"
  protected val typeColumn = "type"

  protected val typeSend = "s"
  protected val typeReceive = "r"
}

object ClearDynamoResultsTable extends App with DynamoResultsTable {
  dynamoClient.scan(new ScanRequest(tableName)).getItems.foreach { i =>
    dynamoClient.deleteItem(
      new DeleteItemRequest().withTableName(tableName).addKeyEntry(resultNameColumn, i.get(resultNameColumn)))
  }
}