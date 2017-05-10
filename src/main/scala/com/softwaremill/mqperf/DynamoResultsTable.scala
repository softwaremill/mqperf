package com.softwaremill.mqperf

import com.amazonaws.services.dynamodbv2.model.{DeleteItemRequest, ScanRequest}
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDB, AmazonDynamoDBClientBuilder}
import com.softwaremill.mqperf.config.AWS
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}

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
  protected val receiveBatchTimerMinColumn = "receive_batch_timer_min"
  protected val receiveBatchTimerMaxColumn = "receive_batch_timer_max"
  protected val receiveBatchTimerMeanColumn = "receive_batch_timer_mean"
  protected val receiveBatchTimerMedianColumn = "receive_batch_timer_median"
  protected val receiveBatchTimerStdDevColumn = "receive_batch_timer_st_dev"
  protected val receiveBatchTimer75thPercentileColumn = "receive_batch_timer_75th_perc"
  protected val receiveBatchTimer95thPercentileColumn = "receive_batch_timer_95th_perc"
  protected val receiveBatchTimer98thPercentileColumn = "receive_batch_timer_98th_perc"
  protected val receiveBatchTimer99thPercentileColumn = "receive_batch_timer_99th_perc"
  protected val clusterTimerMinColumn = "cluster_timer_min"
  protected val clusterTimerMaxColumn = "cluster_timer_max"
  protected val clusterTimerMeanColumn = "cluster_timer_mean"
  protected val clusterTimerMedianColumn = "cluster_timer_median"
  protected val clusterTimerStdDevColumn = "cluster_timer_st_dev"
  protected val clusterTimer75thPercentileColumn = "cluster_timer_75th_perc"
  protected val clusterTimer95thPercentileColumn = "cluster_timer_95th_perc"
  protected val clusterTimer98thPercentileColumn = "cluster_timer_98th_perc"
  protected val clusterTimer99thPercentileColumn = "cluster_timer_99th_perc"
  protected val msgsCountColumn = "msgs_count"

  protected val timestampFormat: DateTimeFormatter = DateTimeFormat.forPattern("YYYY-MM-dd'T'HH:mm")
}

object ClearDynamoResultsTable extends App with DynamoResultsTable {
  dynamoClient.scan(new ScanRequest(tableName)).getItems.foreach { i =>
    dynamoClient.deleteItem(
      new DeleteItemRequest().withTableName(tableName).addKeyEntry(resultNameColumn, i.get(resultNameColumn))
    )
  }
}
