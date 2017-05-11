package com.softwaremill.mqperf

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
import java.util.concurrent.{ExecutorService, Executors}

import akka.dispatch.ExecutionContexts
import com.softwaremill.mqperf.mq.Mq
import com.softwaremill.mqperf.util.FakeClock
import com.timgroup.statsd.NoOpStatsDClient
import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers, Suite}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class ReceiverRunnableTest extends FlatSpec with ReceiverTestKit {

  behavior of "ReceiverRunnable"

  it should "wait for idle timeout before stop" in new ReceiverTestCase {
    receiverShouldBeRunning
    queueShouldBeEmpty

    sendMsgAndWaitForReceive()
    receiverShouldBeRunning

    // Almost max idle time
    addClockTicks(receiverTimeout - 1.nano)
    receiverShouldBeRunning

    // Exceed the idle timeout
    addClockTicks(1.nano)
    receiverShouldBeStopped
  }

  it should "reset idle time after receiving any message" in new ReceiverTestCase {
    receiverShouldBeRunning
    queueShouldBeEmpty

    sendMsgAndWaitForReceive()

    receiverShouldBeRunning
    // Almost max idle time
    addClockTicks(receiverTimeout - 1.nano)
    receiverShouldBeRunning

    sendMsgAndWaitForReceive()

    receiverShouldBeRunning
    // Almost max idle time
    addClockTicks(receiverTimeout - 1.nano)
    receiverShouldBeRunning

    // Exceed the idle timeout
    addClockTicks(1.nano)
    receiverShouldBeStopped
  }

  it should "not timeout before receiving any message" in new ReceiverTestCase {
    receiverShouldBeRunning
    queueShouldBeEmpty

    // Exceed the idle timeout
    addClockTicks(receiverTimeout * 10)

    receiverShouldBeRunning
    queueShouldBeEmpty

    sendMsgAndWaitForReceive()
    receiverShouldBeRunning

    // Exceed the idle timeout
    addClockTicks(receiverTimeout)
    receiverShouldBeStopped
  }

}

trait ReceiverTestKit extends Matchers with Eventually with BeforeAndAfterAll {
  self: Suite =>

  val executor: ExecutorService = Executors.newCachedThreadPool()
  implicit val ec: ExecutionContext = ExecutionContexts.fromExecutor(executor)

  override protected def afterAll(): Unit = {
    super.afterAll()
    executor.shutdownNow()
  }

  trait ReceiverTestCase {
    private val fakeMq = new FakeMq
    private val fakeClock = new FakeClock
    private val receiverRunnable = new ReceiverRunnable(
      fakeMq,
      "fakeMq",
      1,
      new DateTime(),
      1,
      new NoOpStatsDClient(),
      fakeClock
    )

    protected lazy val receiverFut: Future[Unit] =
      Future {
        receiverRunnable.run()
      }

    protected val receiverTimeout: FiniteDuration = receiverRunnable.timeout

    def addClockTicks(duration: FiniteDuration): Unit = {
      fakeClock.add(duration)
    }

    def queueShouldBeEmpty: Unit = eventually {
      fakeMq.isEmpty shouldBe true
    }

    def receiverShouldBeRunning: Unit = receiverFut.isCompleted shouldBe false

    def receiverShouldBeStopped: Unit = eventually {
      receiverFut.isCompleted shouldBe true
    }

    def sendMsgAndWaitForReceive(): Unit = {
      val numberOfMsgs = 1
      val totalAckBefore = fakeMq.totalAcknowledged
      fakeMq.put(numberOfMsgs)
      // Be sure that message has been processed and lastReceived won't be updated AFTER we add clock ticks.
      eventually {
        fakeMq.totalAcknowledged shouldBe >=(totalAckBefore + numberOfMsgs)
      }
    }
  }

}

class FakeMq extends Mq {

  import scala.compat.java8.FunctionConverters._

  override val config = ConfigFactory.empty()

  override type MsgId = Long

  private val queueSize = new AtomicInteger(0)

  private val acknowledged = new AtomicLong(0)

  override def createSender(): MqSender = new FakeMqSender

  override def createReceiver(): MqReceiver = new FakeMqReceiver

  def put(n: Int): Unit = queueSize.accumulateAndGet(n, asJavaIntBinaryOperator(_ + _))

  def isEmpty: Boolean = queueSize.get() == 0

  def totalAcknowledged: Long = acknowledged.get()

  class FakeMqReceiver extends MqReceiver {
    override def receive(maxMsgCount: Int): List[(Long, String)] = {
      val prev = queueSize.getAndAccumulate(maxMsgCount, asJavaIntBinaryOperator {
        (actual, x) => Math.max(actual - x, 0)
      })
      val numOfReceivedMsgs: Int = if (prev < maxMsgCount) {
        prev
      }
      else {
        maxMsgCount
      }
      List.fill(numOfReceivedMsgs) {
        // Has to be parsable to a Long number
        val msgBody = System.currentTimeMillis().toString
        (1L, msgBody)
      }
    }

    override def ack(ids: List[Long]): Unit = {
      acknowledged.accumulateAndGet(ids.size, asJavaLongBinaryOperator(_ + _))
    }
  }

  class FakeMqSender extends MqSender {

    override def send(msgs: List[String]): Unit = queueSize.accumulateAndGet(msgs.size, asJavaIntBinaryOperator(_ + _))
  }

}

