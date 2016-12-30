package com.softwaremill.mqperf.mq

import java.util.concurrent.TimeUnit

import com.mongodb._
import com.mongodb.client.model.{CreateCollectionOptions, UpdateOptions}
import com.mongodb.client.{MongoCollection, MongoCursor, MongoDatabase}
import org.bson.Document
import org.bson.types.ObjectId

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.concurrent.Future

class MongoCappedMq(configMap: Map[String, String]) extends Mq {

  import MongoCappedMq._

  override type MsgId = ObjectId

  private val client = new MongoClient(configMap("host"))

  private val concern = if (configMap("write_concern") == "replica")
    WriteConcern.W2 else WriteConcern.ACKNOWLEDGED

  private val db: MongoDatabase = client.getDatabase("mq").withWriteConcern(concern)

  private val queueColl: MongoCollection[Document] = {
    if (!db.listCollectionNames().iterator().asScala.contains(QueueCollectionName)) {
      val options = new CreateCollectionOptions()
        .capped(true)
        .sizeInBytes(configMap("queue_size_in_bytes").toLong)
      db.createCollection(QueueCollectionName, options)
    }
    db.getCollection(QueueCollectionName)
  }

  private val cursorStateColl: MongoCollection[Document] = {
    if (!db.listCollectionNames().iterator().asScala.contains(CursorStateCollectionName)) {
      db.createCollection(CursorStateCollectionName)
    }
    db.getCollection(CursorStateCollectionName)
  }

  override def createSender(): MqSender = new MongoCappedMqSender(queueColl)

  override def createReceiver(): MqReceiver = new MongoCappedMqReceiver(queueColl, cursorStateColl)

  class MongoCappedMqSender(queueColl: MongoCollection[Document]) extends MqSender {

    /**
     * Synchronous - must wait for the messages to be sent
     */
    override def send(msgs: List[String]): Unit = {
      val messagesToInsert: java.util.List[Document] = msgs.map { msg =>
        new Document(MessageField, msg)
      }.asJava
      queueColl.insertMany(messagesToInsert)
    }
  }

  class MongoCappedMqReceiver(queueColl: MongoCollection[Document], cursorStateColl: MongoCollection[Document]) extends MqReceiver {

    private val nodeId: Int = ObjectId.getGeneratedMachineIdentifier

    private val cursor: MongoCursor[Document] = {
      val existingCursorState = cursorStateColl.find(new BasicDBObject(NodeIdField, nodeId)).first()
      val filter = if (existingCursorState == null) {
        new BasicDBObject()
      }
      else {
        val lastDocId = existingCursorState.getObjectId(LastDocField)
        new BasicDBObject(IdField, new BasicDBObject("$gt", lastDocId))
      }

      // TODO handle lost capped position
      queueColl
        .find(filter)
        .sort(new BasicDBObject("$natural", 1))
        .cursorType(CursorType.TailableAwait)
        .maxAwaitTime(configMap("await_ms").toInt, TimeUnit.MILLISECONDS)
        .batchSize(configMap("batch_size").toInt)
        .iterator()
    }

    override def receive(maxMsgCount: Int): List[(ObjectId, String)] = {
      doReceive(Nil, maxMsgCount)
    }

    @tailrec
    private def doReceive(acc: List[(ObjectId, String)], limit: Int): List[(ObjectId, String)] = {
      if (limit > 0) {
        val msg = cursor.tryNext()
        if (msg == null) {
          acc
        }
        else {
          val newElement = msg.getObjectId(IdField) -> msg.getString(MessageField)
          doReceive(newElement :: acc, limit - 1)
        }
      }
      else {
        acc
      }
    }

    /**
     * Can be asynchronous
     */
    override def ack(ids: List[ObjectId]): Unit = {
      import scala.concurrent.ExecutionContext.Implicits.global
      Future {
        val lastSeenId = ids.max
        cursorStateColl.updateOne(new BasicDBObject(NodeIdField, nodeId), new BasicDBObject("$set", new BasicDBObject(LastDocField, lastSeenId)), new UpdateOptions().upsert(true))
      }
    }
  }

}

object MongoCappedMq {
  val QueueCollectionName = "mq"
  val CursorStateCollectionName = "mq_cursors"

  val IdField = "_id"
  val MessageField = "message"

  val NodeIdField = "nodeId"
  val LastDocField = "lastDoc"
}
