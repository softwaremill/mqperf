package mqperf.rabbitmq

import com.rabbitmq.client._
import com.typesafe.scalalogging.StrictLogging
import mqperf.{Config, Mq, MqReceiver, MqSender}

import java.util.concurrent.ConcurrentLinkedQueue
import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, blocking}
import scala.util.Try

class RabbitMq extends Mq with StrictLogging {
  private val HostsConfigKey = "hosts"
  private val QueueNameConfigKey = "queueName"
  private val NrPollReattemptConfigKey = "nrPollReattempt"

  var conn: Connection = _

  override def init(config: Config): Unit = {
    val host: String = config.mqConfig(HostsConfigKey)
    val queueName: String = config.mqConfig(QueueNameConfigKey)

    val cf = new ConnectionFactory()
    cf.setHost(host)
    cf.setUsername("guest")
    cf.setPassword("guest")

    try {
      conn = cf.newConnection()

      newChannel(queueName, passive = false)
      logger.info(s"Created queue $queueName")
    } catch {
      case e: Exception => logger.warn(s"Queue $queueName creation failed", e)
    }
  }

  private def newChannel(queueName: String, passive: Boolean): Channel = {
    val channel = conn.createChannel()
    if (passive) channel.queueDeclarePassive(queueName)
    else channel.queueDeclare(queueName, false, false, false, null)

    channel
  }

  override def createSender(config: Config): MqSender = new MqSender {
    private val queueName: String = config.mqConfig(QueueNameConfigKey)
    private val channel = newChannel(queueName, passive = true)
    channel.confirmSelect()

    override def send(msgs: Seq[String]): Future[Unit] = Future {
      msgs.foreach(msg => channel.basicPublish("", queueName, MessageProperties.PERSISTENT_TEXT_PLAIN, msg.getBytes))
    }
  }

  override def createReceiver(config: Config): MqReceiver = new MqReceiver {
    override type MsgId = Long

    private val queueName: String = config.mqConfig(QueueNameConfigKey)
    private val nrPollReattempt: Int = Try(config.mqConfig(NrPollReattemptConfigKey).toInt).getOrElse(0)
    private val channel = newChannel(queueName, passive = true)
    channel.basicQos(config.mqConfig("qos").toInt, false)

    private val queue = new ConcurrentLinkedQueue[(MsgId, String)]()

    private val consumer = new DefaultConsumer(channel) {
      override def handleDelivery(
          consumerTag: String,
          envelope: Envelope,
          properties: AMQP.BasicProperties,
          body: Array[Byte]
      ): Unit = {

        queue.add((envelope.getDeliveryTag, new String(body, "UTF-8")))
      }
    }

    channel.basicConsume(queueName, false, consumer)

    override def receive(maxMsgCount: Int): Future[Seq[(MsgId, String)]] = Future {
      blocking {

        @tailrec
        def doReceive(acc: Seq[(MsgId, String)], count: Int): Seq[(MsgId, String)] = {
          if (count == 0) {
            acc
          } else {
            nextMessageFromQueue(waitForMsgs = acc.isEmpty) match {
              case None    => acc
              case Some(m) => doReceive(acc :+ m, count - 1)
            }
          }
        }

        def nextMessageFromQueue(waitForMsgs: Boolean): Option[(MsgId, String)] = {
          @tailrec
          def doPoll(waitIterations: Int): Option[(MsgId, String)] = {

            val next = queue.poll()
            if (next == null) {
              if (waitIterations > 0) {
                Thread.sleep(100L)
                doPoll(waitIterations - 1)
              } else None
            } else Some(next)
          }

          doPoll(if (waitForMsgs) nrPollReattempt else 0)
        }

        doReceive(Nil, maxMsgCount)
      }
    }

    private val multipleAck = Try(config.mqConfig("multipleAck").toBoolean).getOrElse(false)

    override def ack(ids: Seq[MsgId]): Future[Unit] = Future.successful {
      if (multipleAck) {
        if (ids.nonEmpty) {
          // as on optimization, we acknowledge multiple messages at once (http://www.rabbitmq.com/confirms.html)
          // This works as delivery tags are channel scoped (and we use one channel per receiver), and we know
          // that all messages in a batch are acknowledged at once.
          channel.basicAck(ids.max, true)
        }
      } else {
        ids.foreach { id =>
          channel.basicAck(id, false)
        }
      }
    }
  }
}
