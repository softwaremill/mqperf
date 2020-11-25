package com.softwaremill.mqperf.mq

import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, FSM, Props, Terminated}
import com.softwaremill.mqperf.config.TestConfig
import eventstore.akka.tcp.ConnectionActor
import eventstore.akka.{LiveProcessingStarted, Settings}
import eventstore.cluster.{ClusterSettings, GossipSeedsOrDns}
import eventstore.core.settings.PersistentSubscriptionSettings
import eventstore.{Content, EventData, WriteEvents, PersistentSubscription => PS}
import eventstore.PersistentSubscription.Nak.Action.Retry
import eventstore.PersistentSubscription.{Ack, Nak}
import eventstore.core.{
  EsException,
  EventNumber,
  EventRecord,
  EventStream,
  Out,
  OutLike,
  ResolvedEvent,
  UserCredentials,
  Uuid,
  WriteEventsCompleted
}
import MyPersistentSubscriptionActor._

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}

class EventStoreMq(testConfig: TestConfig) extends Mq {

  private val StreamId = "mqperf-stream"
  private val GroupName = "mqperf-group"

  private val system = ActorSystem()
  private val firstHost = testConfig.brokerHosts.head
  private val settings = Settings(
    address = new InetSocketAddress(firstHost, 1113),
    cluster = Some(
      ClusterSettings(
        gossipSeedsOrDns = GossipSeedsOrDns(testConfig.brokerHosts.map(h => new InetSocketAddress(h, 2113)): _*)
      )
    )
  )

  private val connection = system.actorOf(ConnectionActor.props(settings))
  connection ! PS.Create(
    EventStream.Id(StreamId),
    GroupName,
    PersistentSubscriptionSettings(readBatchSize = 1000, historyBufferSize = 2000, maxCheckPointCount = 10000)
  )

  override type MsgId = UUID

  private lazy val receiveActor = system.actorOf(Props(new AddToBufferActor))
  private lazy val subscriptionActor = system.actorOf(
    MyPersistentSubscriptionActor
      .props(connection, receiveActor, EventStream.Id(StreamId), GroupName, None, settings, autoAck = false)
  )

  private val msgBuffer = new ConcurrentLinkedQueue[(UUID, String)]()

  private class AddToBufferActor extends Actor with ActorLogging {
    def receive: Receive = {
      case event: EventRecord =>
        msgBuffer.offer((event.data.eventId, event.data.data.value.utf8String))
      case LiveProcessingStarted =>
    }
  }

  override def createReceiver(): MqReceiver =
    new MqReceiver {
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
        subscriptionActor ! ManualAck(ids)
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

  override def createSender(): MqSender =
    (msgs: List[String]) => {
      val events = msgs.map(m => EventData("e", data = Content(m)))
      val p = Promise[Unit]()
      implicit val writeListener: ActorRef = system.actorOf(Props(new WriteListener(p)))
      connection ! WriteEvents(EventStream.Id(StreamId), events)
      Await.result(p.future, 10.seconds)
    }

  override def close(): Unit = {
    Await.result(system.terminate(), 60.seconds)
  }
}

/*
A customized PersistentSubscriptionActor from the JVM client:
- max in flight messages bumped to 1000 (buffer size in subscribeToPersistentStream)
- ManualAck changed to accept multiple event ids
 */
object MyPersistentSubscriptionActor {

  def props(
      connection: ActorRef,
      client: ActorRef,
      streamId: EventStream.Id,
      groupName: String,
      credentials: Option[UserCredentials],
      settings: Settings,
      autoAck: Boolean = true
  ): Props = {
    Props(
      new MyPersistentSubscriptionActor(
        connection,
        client,
        streamId,
        groupName,
        credentials,
        settings,
        autoAck
      )
    )
  }

  sealed trait State
  case object Unsubscribed extends State
  case object LiveProcessing extends State
  case object CatchingUp extends State

  sealed trait Data
  final case object ConnectionDetails extends Data
  final case class SubscriptionDetails(subscriptionId: String, lastEventNum: Option[EventNumber.Exact]) extends Data

  final case class ManualAck(eventIds: List[Uuid])
  final case class ManualNak(eventId: Uuid)
}

class MyPersistentSubscriptionActor(
    val connection: ActorRef,
    val client: ActorRef,
    val streamId: EventStream,
    val groupName: String,
    val credentials: Option[UserCredentials],
    val settings: Settings,
    val autoAck: Boolean
) extends Actor
    with ActorLogging
    with FSM[State, Data] {

  context watch client
  context watch connection

  type Next = EventNumber.Exact
  type Last = Option[EventNumber.Exact]

  def toConnection(x: Out) = connection ! credentials.fold[OutLike](x)(x.withCredentials)
  def toClient(event: Event) = client ! event
  def subscribeToPersistentStream(): Unit =
    toConnection(PS.Connect(EventStream.Id(streamId.streamId), groupName, bufferSize = 1000))

  private def connectionDetails = ConnectionDetails

  private def subscriptionDetails(subId: String, lastEventNum: Last): SubscriptionDetails =
    SubscriptionDetails(subId, lastEventNum)

  def getEventId(e: eventstore.Event): Uuid =
    e match {
      case x: ResolvedEvent => x.linkEvent.data.eventId
      case x                => x.data.eventId
    }

  startWith(Unsubscribed, connectionDetails)

  onTransition {
    case _ -> Unsubscribed   => subscribeToPersistentStream() // try to (re-)connect.
    case _ -> LiveProcessing => client ! LiveProcessingStarted
  }

  when(Unsubscribed) {
    case Event(PS.Connected(subId, _, eventNum), _) =>
      val subDetails = subscriptionDetails(subId, eventNum)
      eventNum match {
        case None => goto(LiveProcessing) using subDetails
        case _    => goto(CatchingUp) using subDetails
      }
    // Ignore events sent while unsubscribed
    case Event(PS.EventAppeared(_), _) =>
      stay()
  }

  when(LiveProcessing) {
    case Event(PS.EventAppeared(event), details: SubscriptionDetails) =>
      if (autoAck) toConnection(Ack(details.subscriptionId, getEventId(event) :: Nil))
      client ! event
      stay()
    case Event(ManualAck(eventIds), details: SubscriptionDetails) =>
      toConnection(Ack(details.subscriptionId, eventIds))
      stay()
    case Event(ManualNak(eventId), details: SubscriptionDetails) =>
      toConnection(Nak(details.subscriptionId, List(eventId), Retry, None))
      stay()
  }

  when(CatchingUp) {
    case Event(PS.EventAppeared(event), details: SubscriptionDetails) =>
      if (autoAck) toConnection(Ack(details.subscriptionId, getEventId(event) :: Nil))
      client ! event
      if (details.lastEventNum.exists(_ <= event.number)) goto(LiveProcessing) using details
      else stay()
    case Event(ManualAck(eventIds), details: SubscriptionDetails) =>
      toConnection(Ack(details.subscriptionId, eventIds))
      stay()
    case Event(ManualNak(eventId), details: SubscriptionDetails) =>
      toConnection(Nak(details.subscriptionId, List(eventId), Retry, None))
      stay()
  }

  whenUnhandled {
    // If a reconnect is launched in LiveProcessing or CatchingUp, then renew subId
    case Event(PS.Connected(subId, _, eventNum), _) =>
      stay() using subscriptionDetails(subId, eventNum)
    // Error conditions
    // This handles when the client or connection is terminated (unrecoverable)
    case Event(Terminated(_), _) =>
      stop()
    // This handles when a generic error has occurred
    case failure @ Event(Failure(e), _) =>
      log.error(e.toString)
      client ! failure
      stop()
    // This is when the subscription is dropped.
    case Event(Unsubscribed, _) =>
      stop()
    case Event(e, s) =>
      log.warning(s"Received unhandled $e in state $stateName with state $s")
      stay()
  }

  initialize()
}
