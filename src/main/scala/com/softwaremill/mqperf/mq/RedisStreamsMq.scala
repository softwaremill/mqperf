package com.softwaremill.mqperf.mq

import com.softwaremill.mqperf.config.TestConfig
import com.typesafe.scalalogging.StrictLogging
import redis.clients.jedis.StreamEntryID.UNRECEIVED_ENTRY
import redis.clients.jedis.params.XReadGroupParams
import redis.clients.jedis.{HostAndPort, JedisPool, StreamEntryID}

import java.util.UUID.randomUUID
import scala.collection.JavaConverters._

class RedisStreamsMq(testConfig: TestConfig) extends Mq with StrictLogging{
  override type MsgId = StreamEntryID

  final val StreamId = "mystream"
  final val ConsumerGroup = "mygroup"
  final val ConsumerId = randomUUID().toString
  final val MessageEntry = "data"

  private val masterLocation = HostAndPort.from(testConfig.brokerHosts.head)
  private val jedisPool: JedisPool = new JedisPool(masterLocation.getHost, masterLocation.getPort)

  override def close() {
    jedisPool.close()
  }

  override def createSender() =  new MqSender {
    private val client = jedisPool.getResource

    override def send(msgs: List[String]): Unit = {
      msgs
        .map(msg => mapAsJavaMap(Map(MessageEntry -> msg)))
        .foreach(redisMap => client.xadd(StreamId, StreamEntryID.NEW_ENTRY, redisMap))
    }
  }

  override def createReceiver() = new MqReceiver {
    private val client = jedisPool.getResource

    override def receive(maxMsgCount: Int): List[(StreamEntryID, String)] = {
      val readParams = new XReadGroupParams().count(maxMsgCount)
      val stream = mapAsJavaMap(Map(StreamId -> UNRECEIVED_ENTRY))
      Option(client.xreadGroup(ConsumerGroup, ConsumerId, readParams, stream))
        .map(x => x.asScala.toList)
        .getOrElse(List.empty)
        .flatMap(resultMap => resultMap.getValue.asScala.toList)
        .map(entry => Tuple2.apply(entry.getID, entry.getFields.get(MessageEntry)))
    }

    override def ack(ids: List[StreamEntryID]): Unit = {
      ids.foreach(id => client.xack(StreamId, ConsumerGroup, id))
    }
  }
}
