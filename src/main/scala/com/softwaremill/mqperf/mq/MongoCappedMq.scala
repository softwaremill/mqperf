package com.softwaremill.mqperf.mq

import java.util.concurrent.Executors

import akka.dispatch.ExecutionContexts
import com.mongodb._
import com.mongodb.client.model.{CreateCollectionOptions, UpdateOptions}
import com.mongodb.client.{MongoCollection, MongoCursor, MongoDatabase}
import com.softwaremill.mqperf.config.TestConfig
import org.bson.Document
import org.bson.types.ObjectId

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.concurrent.Future

class MongoCappedMq(testConfig: TestConfig) extends Mq {

  import MongoCappedMq._

  override type MsgId = ObjectId

  private val client = new MongoClient(
    testConfig.brokerHosts
      .map(TestConfig.parseHostPort)
      .map {
        case (host, Some(port)) => new ServerAddress(host, port)
        case (host, None)       => new ServerAddress(host)
      }
      .asJava
  )

  private val concern = new WriteConcern(testConfig.mqConfig.getInt("write_concern"))

  private val db: MongoDatabase = client.getDatabase("mq").withWriteConcern(concern)

  private val queueColl: MongoCollection[Document] = {
    if (!db.listCollectionNames().iterator().asScala.contains(QueueCollectionName)) {
      val options = new CreateCollectionOptions()
        .capped(true)
        .sizeInBytes(testConfig.mqConfig.getLong("queue_size_in_bytes"))
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

  // This execution context is shared between all MongoCappedMqReceiver instances
  private val ackExecutionContext = ExecutionContexts.fromExecutor(Executors.newCachedThreadPool())

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

  class MongoCappedMqReceiver(queueColl: MongoCollection[Document], cursorStateColl: MongoCollection[Document])
      extends MqReceiver {

    private val nodeId: Int = ObjectId.getGeneratedMachineIdentifier

    private var cursor = createCursor

    private def createCursor: MongoCursor[Document] = {
      val existingCursorState = cursorStateColl.find(new BasicDBObject(NodeIdField, nodeId)).first()
      val filter = if (existingCursorState == null) {
        new BasicDBObject()
      } else {
        val lastDocId = existingCursorState.getObjectId(LastDocField)
        new BasicDBObject(IdField, new BasicDBObject("$gt", lastDocId))
      }

      // TODO handle lost capped position
      queueColl
        .find(filter)
        .sort(new BasicDBObject("$natural", 1))
        .cursorType(CursorType.Tailable)
        .batchSize(testConfig.mqConfig.getInt("batch_size"))
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
          if (cursor.getServerCursor == null) {
            cursor.close()
            cursor = createCursor
          }
          acc
        } else {
          val newElement = msg.getObjectId(IdField) -> msg.getString(MessageField)
          doReceive(newElement :: acc, limit - 1)
        }
      } else {
        acc
      }
    }

    /**
      * Can be asynchronous
      */
    override def ack(ids: List[ObjectId]): Unit = {
      implicit val ec = ackExecutionContext
      Future {
        val lastSeenId = ids.max
        cursorStateColl.updateOne(
          new BasicDBObject(NodeIdField, nodeId),
          new BasicDBObject("$set", new BasicDBObject(LastDocField, lastSeenId)),
          new UpdateOptions().upsert(true)
        )
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
