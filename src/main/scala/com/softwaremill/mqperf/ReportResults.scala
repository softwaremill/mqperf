package com.softwaremill.mqperf

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, PutItemRequest}
import scala.util.Random
import java.text.SimpleDateFormat
import java.util.Date
import com.softwaremill.mqperf.config.AWSCredentialsFromEnv
import com.amazonaws.regions.{Regions, Region}

class ReportResults(testConfigName: String) {
  private val dynamoClient = {
    val c = new AmazonDynamoDBClient(AWSCredentialsFromEnv())
    c.setRegion(Region.getRegion(Regions.EU_WEST_1))
    c
  }
  private val tableName = "mqperf-results"

  def reportSendingComplete(start: Long, end: Long) {
    val df = newDateFormat

    val testResultName = s"${System.currentTimeMillis()}-$testConfigName-s-${Random.nextInt(1000)}"
    val took = (end - start).toString
    val startStr = new Date(start)
    val endStr = new Date(end)

    dynamoClient.putItem(new PutItemRequest()
      .withTableName(tableName)
      .addItemEntry("test_result_name", new AttributeValue(testResultName))
      .addItemEntry("took", new AttributeValue().withN(took))
      .addItemEntry("start", new AttributeValue(df.format(startStr)))
      .addItemEntry("end", new AttributeValue(df.format(endStr)))
    )

    println(s"$testResultName: $took ($startStr -> $endStr")
  }

  def reportReceivingComplete(start: Long, end: Long, msgsReceived: Int) {
    val df = newDateFormat

    val testResultName = s"${System.currentTimeMillis()}-$testConfigName-r-${Random.nextInt(1000)}"
    val took = (end - start).toString
    val startStr = new Date(start)
    val endStr = new Date(end)

    dynamoClient.putItem(new PutItemRequest()
      .withTableName(tableName)
      .addItemEntry("test_result_name", new AttributeValue(testResultName))
      .addItemEntry("msgs_received", new AttributeValue().withN(msgsReceived.toString))
      .addItemEntry("took", new AttributeValue().withN(took))
      .addItemEntry("start", new AttributeValue(df.format(startStr)))
      .addItemEntry("end", new AttributeValue(df.format(endStr)))
    )

    println(s"$testResultName (${msgsReceived.toString}): $took ($startStr -> $endStr")
  }

  def newDateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss")
}
