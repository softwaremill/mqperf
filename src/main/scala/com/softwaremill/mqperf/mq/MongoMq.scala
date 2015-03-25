package com.softwaremill.mqperf.mq

import com.mongodb.{DBObject, BasicDBObject, WriteConcern, MongoClient}
import org.bson.types.ObjectId

class MongoMq(configMap: Map[String, String]) extends Mq {

  private val IdField = "_id"
  private val NextDeliveryField = "next_delivery"
  private val MessageField = "message"

  private val VisibilityTimeoutMillis = 10 * 1000L

  private val client = new MongoClient(configMap("host"))

  private val db = client.getDB("mq")

  private val concern = if (configMap("write_concern") == "replica")
    WriteConcern.REPLICA_ACKNOWLEDGED else WriteConcern.ACKNOWLEDGED

  private val coll = {
    val c = db.getCollection("mq")

    val nextDeliveryIndex = new BasicDBObject()
    nextDeliveryIndex.put(NextDeliveryField, 1)
    c.createIndex(nextDeliveryIndex)

    c.setWriteConcern(concern)

    c
  }

  override type MsgId = ObjectId

  override def createSender() = new MqSender {
    override def send(msgs: List[String]) = {
      val docs = msgs.map { msg =>
        val doc = new BasicDBObject()
        doc.put(MessageField, msg)
        doc.put(NextDeliveryField, System.currentTimeMillis())
        doc
      }

      coll.insert(docs.toArray[DBObject], concern)
    }
  }

  override def createReceiver() = new MqReceiver {
    override def receive(maxMsgCount: Int) = {
      if (maxMsgCount == 0) {
        Nil
      } else {
        receiveSingle() match {
          case None => Nil
          case Some(idAndMsg) => idAndMsg :: receive(maxMsgCount - 1)
        }
      }
    }

    private def receiveSingle() = {
      val now = System.currentTimeMillis()

      val lteNow = new BasicDBObject()
      lteNow.put("$lte", now)

      val query = new BasicDBObject()
      query.put(NextDeliveryField, lteNow)

      val newNextDelivery = new BasicDBObject()
      newNextDelivery.put(NextDeliveryField, now + VisibilityTimeoutMillis)

      val mutations = new BasicDBObject()
      mutations.put("$set", newNextDelivery)

      val result = coll.findAndModify(query, mutations)

      if (result == null) {
        None
      } else {
        val id = result.get(IdField).asInstanceOf[ObjectId]
        val messageContent = result.get(MessageField).asInstanceOf[String]
        Some((id, messageContent))
      }
    }

    override def ack(ids: List[MsgId]) = {
      ids.foreach { id =>
        val doc = new BasicDBObject()
        doc.put(IdField, id)

        coll.remove(doc, WriteConcern.UNACKNOWLEDGED)
      }
    }
  }
}
