package com.softwaremill.mqperf.mq

import com.mongodb._
import com.softwaremill.mqperf.config.TestConfig
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class MongoMq(testConfig: TestConfig) extends Mq {

  private val IdField = "_id"
  private val NextDeliveryField = "next_delivery"
  private val MessageField = "message"

  private val VisibilityTimeoutMillis = 10 * 1000L

  private val client = new MongoClient(testConfig.brokerHosts.map(TestConfig.parseHostPort).map {
    case (host, Some(port)) => new ServerAddress(host, port)
    case (host, None) => new ServerAddress(host)
  }.asJava)

  private val concern = new WriteConcern(testConfig.mqConfig.getInt("write_concern"))

  private val db = client.getDatabase("mq")

  private val (ackColl, unackColl) = {
    val c = db.getCollection("mq")

    val nextDeliveryIndex = new Document()
      .append(NextDeliveryField, 1)
    c.createIndex(nextDeliveryIndex)

    (c.withWriteConcern(concern), c.withWriteConcern(WriteConcern.UNACKNOWLEDGED))
  }

  override type MsgId = ObjectId

  override def createSender() = new MqSender {
    override def send(msgs: List[String]): Unit = {
      val docs = msgs.map { msg =>
        new Document()
          .append(MessageField, msg)
          .append(NextDeliveryField, System.currentTimeMillis())
      }

      ackColl.insertMany(docs.asJava)
    }
  }

  override def createReceiver() = new MqReceiver {
    override def receive(maxMsgCount: Int): List[(ObjectId, String)] = {
      if (maxMsgCount == 0) {
        Nil
      }
      else {
        receiveSingle() match {
          case None => Nil
          case Some(idAndMsg) => idAndMsg :: receive(maxMsgCount - 1)
        }
      }
    }

    private def receiveSingle() = {
      val now = System.currentTimeMillis()

      val lteNow = new Document()
        .append("$lte", now)

      val query = new Document()
        .append(NextDeliveryField, lteNow)

      val newNextDelivery = new Document()
        .append(NextDeliveryField, now + VisibilityTimeoutMillis)

      val mutations = new Document()
        .append("$set", newNextDelivery)

      val result = unackColl.findOneAndUpdate(query, mutations)

      if (result == null) {
        None
      }
      else {
        val id = result.getObjectId(IdField)
        val messageContent = result.getString(MessageField)
        Some((id, messageContent))
      }
    }

    override def ack(ids: List[MsgId]): Unit = {
      ids.foreach { id =>
        val doc = new Document()
          .append(IdField, id)

        unackColl.deleteOne(doc)
      }
    }
  }
}
