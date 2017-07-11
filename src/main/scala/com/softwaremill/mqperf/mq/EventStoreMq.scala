package com.softwaremill.mqperf.mq

import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.softwaremill.mqperf.config.TestConfig
import eventstore.PersistentSubscriptionActor.ManualAck
import eventstore.cluster.{ClusterSettings, GossipSeedsOrDns}
import eventstore.tcp.ConnectionActor
import eventstore.{Content, EsException, EventData, EventRecord, EventStream, LiveProcessingStarted, PersistentSubscription, PersistentSubscriptionActor, Settings, WriteEvents, WriteEventsCompleted}

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}

class EventStoreMq(testConfig: TestConfig) extends Mq {

  private val StreamId = "mqperf-stream"
  private val GroupName = "mqperf-group"

  private val system = ActorSystem()
  private val settings = Settings(
    //address = new InetSocketAddress("127.0.0.1", 1113),
    cluster = Some(ClusterSettings(
      gossipSeedsOrDns = GossipSeedsOrDns(testConfig.brokerHosts.map(h => new InetSocketAddress(h, 1113)): _*)
    ))
  )

  private val connection = system.actorOf(ConnectionActor.props(settings))
  connection ! PersistentSubscription.Create(EventStream.Id(StreamId), GroupName)

  override type MsgId = UUID

  private lazy val receiveActor = system.actorOf(Props(new AddToBufferActor))
  private lazy val subscriptionActor = system.actorOf(
    PersistentSubscriptionActor.props(connection, receiveActor, EventStream.Id(StreamId),
      GroupName, None, settings, autoAck = false))

  private val msgBuffer = new ConcurrentLinkedQueue[(UUID, String)]()

  private class AddToBufferActor extends Actor with ActorLogging {
    def receive: Receive = {
      case event: EventRecord =>
        msgBuffer.offer((event.data.eventId, event.data.data.value.utf8String))
      case LiveProcessingStarted =>
    }
  }

  override def createReceiver(): MqReceiver = new MqReceiver {
    subscriptionActor // force creation

    override def receive(maxMsgCount: Int): List[(UUID, String)] = {
      doReceive(Nil, waitForMsgs = 10, maxMsgCount)
    }

    @tailrec
    private def doReceive(acc: List[(UUID, String)], waitForMsgs: Int, count: Int): List[(UUID, String)] = {
      if (count == 0) {
        acc
      } else {
        val message = msgBuffer.poll()
        if (message == null && waitForMsgs > 0) {
          Thread.sleep(100L)
          doReceive(acc, waitForMsgs - 1, count)
        } else if (message == null) {
          acc
        } else {
          doReceive(message :: acc, 0, count - 1)
        }
      }
    }

    override def ack(ids: List[UUID]): Unit = {
      ids.foreach(id => subscriptionActor ! ManualAck(id))
    }
  }

  private class WriteListener(p: Promise[Unit]) extends Actor with ActorLogging {
    def receive: Receive = {
      case WriteEventsCompleted(_, _) =>
        p.success(())
        context.stop(self)

      case Failure(e: EsException) =>
        p.failure(e)
        context.stop(self)
    }
  }

  override def createSender(): MqSender = new MqSender {
    override def send(msgs: List[String]): Unit = {
      val events = msgs.map(m => EventData("e", data = Content(m)))
      val p = Promise[Unit]()
      implicit val writeListener = system.actorOf(Props(new WriteListener(p)))
      connection ! WriteEvents(EventStream.Id(StreamId), events)
      Await.result(p.future, 10.seconds)
    }
  }

  override def close(): Unit = {
    system.terminate()
  }
}
