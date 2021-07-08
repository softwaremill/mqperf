package com.softwaremill.mqperf.mq

import com.softwaremill.mqperf.config.TestConfig
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import redis.clients.jedis.StreamEntryID.UNRECEIVED_ENTRY
import redis.clients.jedis.params.XReadGroupParams
import redis.clients.jedis.{HostAndPort, JedisCluster, StreamEntryID}

import java.util
import java.util.UUID.randomUUID
import scala.collection.JavaConverters._
import scala.collection.immutable.Seq
import scala.util.Random

class RedisStreamsMq(testConfig: TestConfig) extends Mq with StrictLogging {
  override type MsgId = RedisMessageId

  val StreamsCount: Int = ConfigFactory.load().getInt("streamsCount")
  val Streams: Seq[String] = List.range(0, StreamsCount).map("stream" + _)
  val ConsumerGroup = "mygroup"
  val ConsumerId: String = randomUUID().toString
  val MessageEntry = "data"
  private val masterLocations = testConfig.brokerHosts.map(HostAndPort.from).toSet
  private val jedis: JedisCluster = new JedisCluster(masterLocations.asJava)

  override def close() {
    jedis.close()
  }

  override def createSender() = new MqSender {

    override def send(msgs: List[String]): Unit = {
      msgs
        .map(msg => mapAsJavaMap(Map(MessageEntry -> msg)))
        .foreach(redisMap => jedis.xadd(selectStream, StreamEntryID.NEW_ENTRY, redisMap))
    }
  }

  private def selectStream = {
    Streams(Random.nextInt(Streams.size))
  }

  override def createReceiver() = new MqReceiver {

    override def receive(maxMsgCount: Int): List[(RedisMessageId, String)] = {
      val readParams = new XReadGroupParams().count(maxMsgCount)
      Streams.map(stream => mapAsJavaMap(Map(stream -> UNRECEIVED_ENTRY))).toList
        .flatMap(stream => xreadGroup(readParams, stream))
        .flatMap(resultMap => resultMap.getValue.asScala.toList.map(streamEntry => (resultMap.getKey, streamEntry)))
        .map(t2 => (RedisMessageId(t2._1, t2._2.getID), t2._2.getFields.get(MessageEntry)))
    }

    private def xreadGroup(readParams: XReadGroupParams, stream: util.Map[String, StreamEntryID]) = {
      Option(jedis.xreadGroup(ConsumerGroup, ConsumerId, readParams, stream)).toList
        .flatMap(_.asScala.toList)
    }

    override def ack(ids: List[RedisMessageId]): Unit = {
      ids.foreach(id => jedis.xack(id.stream, ConsumerGroup, id.streamEntryID))
    }
  }

  case class RedisMessageId(stream: String, streamEntryID: StreamEntryID) {}
}
