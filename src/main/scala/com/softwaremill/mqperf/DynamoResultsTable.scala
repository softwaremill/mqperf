package com.softwaremill.mqperf

import com.amazonaws.services.dynamodbv2.model.{DeleteItemRequest, ScanRequest}
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDB, AmazonDynamoDBClientBuilder}
import com.softwaremill.mqperf.config.AWS

import scala.collection.JavaConversions._

trait DynamoResultsTable {
  protected val dynamoClient: AmazonDynamoDB =
    AmazonDynamoDBClientBuilder
      .standard()
      .withCredentials(AWS.CredentialProvider)
      .withRegion(AWS.DefaultRegion)
      .build()

  protected val tableName = "mqperf-results"
  protected val resultNameColumn = "test_result_name"
  protected val resultTimestampColumn = "test_result_timestamp"
  protected val meterMean = "meter_mean"
  protected val meter1MinuteEwma = "meter_1min_ewma"
  protected val meter5MinuteEwma = "meter_5min_ewma"
  protected val meter15MinuteEwma = "meter_15min_ewma"
  protected val receiveBatchTimerMinColumn = "receiveBatchTimer_min"
  protected val receiveBatchTimerMaxColumn = "receiveBatchTimer_max"
  protected val receiveBatchTimerMeanColumn = "receiveBatchTimer_mean"
  protected val receiveBatchTimerMedianColumn = "receiveBatchTimer_median"
  protected val receiveBatchTimerStdDevColumn = "receiveBatchTimer_st_dev"
  protected val receiveBatchTimer75thPercentileColumn = "receiveBatchTimer_75th_perc"
  protected val receiveBatchTimer95thPercentileColumn = "receiveBatchTimer_95th_perc"
  protected val receiveBatchTimer98thPercentileColumn = "receiveBatchTimer_98th_perc"
  protected val receiveBatchTimer99thPercentileColumn = "receiveBatchTimer_99th_perc"
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
  dynamoClient.scan(new ScanRequest(tableName)).getItems.foreach { i =>
    dynamoClient.deleteItem(
      new DeleteItemRequest().withTableName(tableName).addKeyEntry(resultNameColumn, i.get(resultNameColumn))
    )
  }
}
