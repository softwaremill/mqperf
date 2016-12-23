package com.softwaremill.mqperf

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.softwaremill.mqperf.config.{AWSCredentialsFromEnv, TestConfigOnS3}
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.dynamodbv2.model.{DeleteItemRequest, ScanRequest}

import scala.collection.JavaConversions._

trait DynamoResultsTable {
  protected val dynamoClientOpt: Option[AmazonDynamoDBClient] =
    for {
      awsCredentails <- AWSCredentialsFromEnv(TestConfigOnS3.LocalConfig)
    } yield {
      val c = new AmazonDynamoDBClient(awsCredentails)
      c.setRegion(Region.getRegion(Regions.EU_WEST_1))
      c
    }

  protected val tableName = "mqperf-results"
  protected val resultNameColumn = "test_result_name"
  protected val histogramMinColumn = "histogram_min"
  protected val histogramMaxColumn = "histogram_max"
  protected val histogramMeanColumn = "histogram_mean"
  protected val histogramMedianColumn = "histogram_median"
  protected val histogramStdDevColumn = "histogram_std_dev"
  protected val histogram75thPercentileColumn = "histogram_75th_perc"
  protected val histogram95thPercentileColumn = "histogram_95th_perc"
  protected val histogram98thPercentileColumn = "histogram_98th_perc"
  protected val histogram99thPercentileColumn = "histogram_99th_perc"
  protected val timerMinColumn = "timer_min"
  protected val timerMaxColumn = "timer_max"
  protected val timerMeanColumn = "timer_mean"
  protected val timerMedianColumn = "timer_median"
  protected val timerStdDevColumn = "timer_st_dev"
  protected val timer75thPercentileColumn = "timer_75th_perc"
  protected val timer95thPercentileColumn = "timer_95th_perc"
  protected val timer98thPercentileColumn = "timer_98th_perc"
  protected val timer99thPercentileColumn = "timer_99th_perc"
  protected val msgsCountColumn = "msgs_count"
  protected val typeColumn = "type"
}

object ClearDynamoResultsTable extends App with DynamoResultsTable {

  for {
    dynamoClient <- dynamoClientOpt
  } {
    dynamoClient.scan(new ScanRequest(tableName)).getItems.foreach { i =>
      dynamoClient.deleteItem(
        new DeleteItemRequest().withTableName(tableName).addKeyEntry(resultNameColumn, i.get(resultNameColumn))
      )
    }
  }

}
