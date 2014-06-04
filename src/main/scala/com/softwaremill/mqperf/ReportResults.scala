package com.softwaremill.mqperf

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, PutItemRequest}
import scala.util.Random
import java.text.SimpleDateFormat
import java.util.Date

class ReportResults(testConfigName: String) {
  private val dynamoClient = new AmazonDynamoDBClient(AWSCredentialsFromEnv())
  private val tableName = "mqperf-results"

  def reportSendingComplete(start: Long, end: Long) {
    val df = newDateFormat

    dynamoClient.putItem(new PutItemRequest()
      .withTableName(tableName)
      .addItemEntry("test_result_name", new AttributeValue(s"${System.currentTimeMillis()}-$testConfigName-s-${Random.nextInt(1000)}"))
      .addItemEntry("took", new AttributeValue().withN((end - start).toString))
      .addItemEntry("start", new AttributeValue(df.format(new Date(start))))
      .addItemEntry("end", new AttributeValue(df.format(new Date(end))))
    )
  }

  def reportReceivingComplete(start: Long, end: Long, msgsReceived: Int) {
    val df = newDateFormat

    dynamoClient.putItem(new PutItemRequest()
      .withTableName(tableName)
      .addItemEntry("test_result_name", new AttributeValue(s"${System.currentTimeMillis()}-$testConfigName-r-${Random.nextInt(1000)}"))
      .addItemEntry("took", new AttributeValue().withN((end - start).toString))
      .addItemEntry("msgs_received", new AttributeValue().withN(msgsReceived.toString))
      .addItemEntry("start", new AttributeValue(df.format(new Date(start))))
      .addItemEntry("end", new AttributeValue(df.format(new Date(end))))
    )
  }

  def newDateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss")
}
