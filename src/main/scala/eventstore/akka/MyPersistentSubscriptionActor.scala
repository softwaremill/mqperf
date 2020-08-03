package eventstore.akka

import akka.actor.Status.Failure
import akka.actor.{ActorRef, FSM, Props, Terminated}
import eventstore.{Event, EventNumber, EventStream, ResolvedEvent, UserCredentials, Uuid}
import eventstore.core.PersistentSubscription.Nak.Action.Retry
import eventstore.{PersistentSubscription => PS}

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

  private case object Unsubscribed extends State

  private case object LiveProcessing extends State

  private case object CatchingUp extends State

  sealed trait Data
  private final case object ConnectionDetails extends Data
  private final case class SubscriptionDetails(subscriptionId: String, lastEventNum: Option[EventNumber.Exact])
      extends Data

  final case class ManualAck(eventIds: List[Uuid])
  final case class ManualNak(eventId: Uuid)
}

/*
A customized PersistentSubscriptionActor from the JVM client:
- max in flight messages bumped to 1000 (buffer size in subscribeToPersistentStream)
- ManualAck changed to accept multiple event ids
See:
- https://github.com/EventStore/EventStore.JVM/issues/157
- https://github.com/EventStore/EventStore.JVM/issues/158
 */
class MyPersistentSubscriptionActor(
    val connection: ActorRef,
    val client: ActorRef,
    val streamId: EventStream,
    val groupName: String,
    val credentials: Option[UserCredentials],
    val settings: Settings,
    val autoAck: Boolean
) extends AbstractPersistentSubscriptionActor[Event]
    with FSM[MyPersistentSubscriptionActor.State, MyPersistentSubscriptionActor.Data] {

  context watch client
  context watch connection

  type Next = EventNumber.Exact
  type Last = Option[EventNumber.Exact]

  private def connectionDetails = MyPersistentSubscriptionActor.ConnectionDetails

  override def subscribeToPersistentStream(): Unit =
    toConnection(PS.Connect(EventStream.Id(streamId.streamId), groupName, 1000))

  private def subscriptionDetails(
      subId: String,
      lastEventNum: Last
  ): MyPersistentSubscriptionActor.SubscriptionDetails =
    MyPersistentSubscriptionActor.SubscriptionDetails(subId, lastEventNum)

  def getEventId(e: eventstore.Event): Uuid =
    e match {
      case x: ResolvedEvent => x.linkEvent.data.eventId
      case x                => x.data.eventId
    }

  startWith(MyPersistentSubscriptionActor.Unsubscribed, connectionDetails)

  onTransition {
    case _ -> MyPersistentSubscriptionActor.Unsubscribed   => subscribeToPersistentStream() // try to (re-)connect.
    case _ -> MyPersistentSubscriptionActor.LiveProcessing => client ! LiveProcessingStarted
  }

  when(MyPersistentSubscriptionActor.Unsubscribed) {
    case Event(PS.Connected(subId, _, eventNum), _) =>
      val subDetails = subscriptionDetails(subId, eventNum)
      eventNum match {
        case None => goto(MyPersistentSubscriptionActor.LiveProcessing) using subDetails
        case _    => goto(MyPersistentSubscriptionActor.CatchingUp) using subDetails
      }
    // Ignore events sent while unsubscribed
    case Event(PS.EventAppeared(_), _) =>
      stay
  }

  when(MyPersistentSubscriptionActor.LiveProcessing) {
    case Event(PS.EventAppeared(event), details: MyPersistentSubscriptionActor.SubscriptionDetails) =>
      if (autoAck) toConnection(PS.Ack(details.subscriptionId, getEventId(event) :: Nil))
      client ! event
      stay
    case Event(
          MyPersistentSubscriptionActor.ManualAck(eventId),
          details: MyPersistentSubscriptionActor.SubscriptionDetails
        ) =>
      toConnection(PS.Ack(details.subscriptionId, eventId))
      stay
    case Event(
          MyPersistentSubscriptionActor.ManualNak(eventId),
          details: MyPersistentSubscriptionActor.SubscriptionDetails
        ) =>
      toConnection(PS.Nak(details.subscriptionId, List(eventId), Retry, None))
      stay
  }

  when(MyPersistentSubscriptionActor.CatchingUp) {
    case Event(PS.EventAppeared(event), details: MyPersistentSubscriptionActor.SubscriptionDetails) =>
      if (autoAck) toConnection(PS.Ack(details.subscriptionId, getEventId(event) :: Nil))
      client ! event
      if (details.lastEventNum.exists(_ <= event.number))
        goto(MyPersistentSubscriptionActor.LiveProcessing) using details
      else stay
    case Event(
          MyPersistentSubscriptionActor.ManualAck(eventId),
          details: MyPersistentSubscriptionActor.SubscriptionDetails
        ) =>
      toConnection(PS.Ack(details.subscriptionId, eventId))
      stay
    case Event(
          MyPersistentSubscriptionActor.ManualNak(eventId),
          details: MyPersistentSubscriptionActor.SubscriptionDetails
        ) =>
      toConnection(PS.Nak(details.subscriptionId, List(eventId), Retry, None))
      stay
  }

  whenUnhandled {
    // If a reconnect is launched in LiveProcessing or CatchingUp, then renew subId
    case Event(PS.Connected(subId, _, eventNum), _) =>
      stay using subscriptionDetails(subId, eventNum)
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
    case Event(MyPersistentSubscriptionActor.Unsubscribed, _) =>
      stop()
    case Event(e, s) =>
      log.warning(s"Received unhandled $e in state $stateName with state $s")
      stay
  }

  initialize()
}
