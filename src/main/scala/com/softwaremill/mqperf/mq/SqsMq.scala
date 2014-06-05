package com.softwaremill.mqperf.mq

import com.amazonaws.services.sqs.buffered.AmazonSQSBufferedAsyncClient
import com.amazonaws.services.sqs.AmazonSQSAsyncClient
import scala.collection.JavaConverters._
import com.amazonaws.services.sqs.model.{DeleteMessageRequest, ReceiveMessageRequest, SendMessageBatchRequestEntry}
import com.softwaremill.mqperf.config.AWSCredentialsFromEnv

class SqsMq extends Mq {
  private val asyncClient = new AmazonSQSAsyncClient(AWSCredentialsFromEnv())
  private val asyncBufferedClient = new AmazonSQSBufferedAsyncClient(asyncClient)

  private val queueUrl = asyncClient.createQueue("mqperf-test-queue").getQueueUrl

  override type MsgId = String

  override def send(msgs: List[String]) = {
    asyncClient.sendMessageBatch(queueUrl,
      msgs.zipWithIndex.map { case (m, i) => new SendMessageBatchRequestEntry(i.toString, m) }.asJava
    )
  }

  override def receive(maxMsgCount: Int) = {
    asyncBufferedClient
      .receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(maxMsgCount))
      .getMessages
      .asScala
      .map(m => (m.getReceiptHandle, m.getBody))
      .toList
  }

  override def ack(ids: List[MsgId]) = {
    ids.foreach { id => asyncBufferedClient.deleteMessageAsync(new DeleteMessageRequest(queueUrl, id)) }
  }
}
