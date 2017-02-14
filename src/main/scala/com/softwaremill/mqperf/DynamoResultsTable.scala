package com.softwaremill.mqperf

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.softwaremill.mqperf.config.{AWSCredentialsFromEnv, TestConfigOnS3}
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.dynamodbv2.model.{DeleteItemRequest, ScanRequest}

import scala.collection.JavaConversions._

trait DynamoResultsTable {
  protected val dynamoClientOpt: Option[AmazonDynamoDBClient] =
    for {
      awsCredentails <- AWSCredentialsFromEnv()
    } yield {
      val c = new AmazonDynamoDBClient(awsCredentails)
      c.setRegion(Region.getRegion(Regions.EU_WEST_1))
      c
    }

  protected val tableName = "mqperf-results"
  protected val resultNameColumn = "test_result_name"
  protected val resultTimestampColumn = "test_result_timestamp"
  protected val meterMean = "meter_mean"
  protected val meter1MinuteEwma = "meter_1min_ewma"
  protected val timerMinColumn = "timer_min"
  protected val timerMaxColumn = "timer_max"
  protected val timerMeanColumn = "timer_mean"
  protected val timerMedianColumn = "timer_median"
  protected val timerStdDevColumn = "timer_st_dev"
  protected val timer75thPercentileColumn = "timer_75th_perc"
  protected val timer95thPercentileColumn = "timer_95th_perc"
  protected val timer98thPercentileColumn = "timer_98th_perc"
  protected val timer99thPercentileColumn = "timer_99th_perc"
  protected val clusterTimerMinColumn = "clusterTimer_min"
  protected val clusterTimerMaxColumn = "clusterTimer_max"
  protected val clusterTimerMeanColumn = "clusterTimer_mean"
  protected val clusterTimerMedianColumn = "clusterTimer_median"
  protected val clusterTimerStdDevColumn = "clusterTimer_st_dev"
  protected val clusterTimer75thPercentileColumn = "clusterTimer_75th_perc"
  protected val clusterTimer95thPercentileColumn = "clusterTimer_95th_perc"
  protected val clusterTimer98thPercentileColumn = "clusterTimer_98th_perc"
  protected val clusterTimer99thPercentileColumn = "clusterTimer_99th_perc"
  protected val msgsCountColumn = "msgs_count"
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
