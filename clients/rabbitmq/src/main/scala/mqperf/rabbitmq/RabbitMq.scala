package mqperf.rabbitmq

import com.rabbitmq.client._
import com.rabbitmq.client.impl.nio.NioParams
import com.typesafe.scalalogging.StrictLogging
import mqperf.{Config, Mq, MqReceiver, MqSender}

import java.util.concurrent.{ConcurrentLinkedQueue, ConcurrentNavigableMap, ConcurrentSkipListMap, LinkedBlockingQueue}
import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise, blocking}

class RabbitMq extends Mq with StrictLogging {
  private val HostsConfigKey = "hosts"
  private val QueueNameConfigKey = "queueName"
  private val MaxPollAttemptsConfigKey = "maxPollAttempts"
  private val UsernameConfigKey = "username"
  private val PasswordConfigKey = "password"
  private val NbIoThreadsConfigKey = "nbNioThreads"
  private val multipleAckConfigKey = "multipleAck"

  private val connections: mutable.Set[Connection] = mutable.Set()

  override def init(config: Config): Unit = {
    val queueName: String = config.mqConfig(QueueNameConfigKey)

    val connection = createConnection(config)
    newChannel(connection, queueName, passive = false)
    logger.info(s"Created queue: $queueName")
  }

  override def cleanUp(config: Config): Unit = {
    val queueName: String = config.mqConfig(QueueNameConfigKey)

    Option(createConnection(config))
      .map(newChannel(_, queueName, passive = true))
      .foreach(_.queueDelete(queueName))
    logger.info(s"Deleted queue $queueName")

    connections.foreach(_.close())
    logger.info("Closed all active connections")
  }

  override def createSender(config: Config): MqSender = new MqSender {
    private val channelPool: LinkedBlockingQueue[Channel] = new LinkedBlockingQueue[Channel]()
    private val queueName: String = config.mqConfig(QueueNameConfigKey)

    private val maxSendInFlight: Int = config.maxSendInFlight
    private val batchSizeSend: Int = config.batchSizeSend
    private val maxChannelsNr: Int = maxSendInFlight / batchSizeSend
    logger.info(s"[Sender] - maxChannelsNr: $maxChannelsNr")

    private val senderConnection: Connection = createConnection(config)

    for (_ <- 1 to maxChannelsNr) {
      val channel = newChannel(senderConnection, queueName, passive = true)
      channel.confirmSelect()
      channelPool.add(channel)
    }

    /*
      Send method uses channels pool in order to be able to send messages using the sender instance on multiple threads simultaneously.
      In order not to be able to send more yet unacked messgages than the MaxSendInFlight setting allows the number of channels is set as a result
      of a following calculation: maxSendInFlight / batchSizeSend.
      If all of the available channels are used at a specific time for sending the other threads will actively wait for the channels to be available
      in the pool again. Only after a succesful channel poll will they be able to publish more messages.
     */
    override def send(msgs: Seq[String]): Future[Unit] = Future {
      blocking {
        val channel = channelPool.take()
        val publishSeqNoPromiseMap: ConcurrentNavigableMap[Long, Promise[Unit]] =
          new ConcurrentSkipListMap() // publishSeqNo are ordered asc

        channel.addConfirmListener(new ConfirmListener {
          override def handleAck(deliveryTag: Long, multiple: Boolean): Unit = {
            // if multiple = true - all messages with publishSeqNo <= deliveryTag has been acked
            if (multiple) {
              publishSeqNoPromiseMap
                .headMap(deliveryTag, true)
                .values()
                .forEach(promise => promise.success())
            } else Option(publishSeqNoPromiseMap.get(deliveryTag)).foreach(_.success())

            publishSeqNoPromiseMap.headMap(deliveryTag, true).clear()
          }

          override def handleNack(deliveryTag: Long, multiple: Boolean): Unit = {
            throw new IllegalStateException(s"NACK for deliveryTag: $deliveryTag")
          }
        })

        Future
          .sequence {
            for (msg <- msgs) yield {
              val promise = Promise[Unit]()
              val nextPublishSeqNo = channel.getNextPublishSeqNo
              publishSeqNoPromiseMap.put(nextPublishSeqNo, promise)

              channel.basicPublish("", queueName, MessageProperties.PERSISTENT_TEXT_PLAIN, msg.getBytes)
              promise.future
            }
          }
          .map(_ => {
            channelPool.add(channel)
            ()
          })
      }
    }
  }

  override def createReceiver(config: Config): MqReceiver = new MqReceiver {
    override type MsgId = Long

    private val queueName: String = config.mqConfig(QueueNameConfigKey)
    private val maxPollAttempts: Int = Option(config.mqConfig(MaxPollAttemptsConfigKey)).map(_.toInt).getOrElse(0)
    private val queue = new ConcurrentLinkedQueue[(MsgId, String)]()

    private val receiverConnection: Connection = createConnection(config)
    private val channel = newChannel(receiverConnection, queueName, passive = true)
    channel.basicQos(config.mqConfig("qos").toInt, false)

    private val multipleAck = Option(config.mqConfig(multipleAckConfigKey)).exists(_.toBoolean)

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
            } else {
              Some(next)
            }
          }

          doPoll(if (waitForMsgs) maxPollAttempts else 0)
        }

        doReceive(Nil, maxMsgCount)
      }
    }


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


  private def createConnection(config: Config): Connection = {
    val host: String = config.mqConfig(HostsConfigKey)
    val username: String = config.mqConfig(UsernameConfigKey)
    val password: String = config.mqConfig(PasswordConfigKey)
    val nbNioThreads: Int = Option(config.mqConfig(NbIoThreadsConfigKey)).map(_.toInt).getOrElse(1)

    val cf = new ConnectionFactory()
    cf.setHost(host)
    cf.setUsername(username)
    cf.setPassword(password)

    cf.useNio()
    cf.setNioParams(new NioParams().setNbIoThreads(nbNioThreads))

    val connection = cf.newConnection()
    connections.add(connection)
    connection
  }

  private def newChannel(conn: Connection, queueName: String, passive: Boolean): Channel = {
    val channel = conn.createChannel()
    if (passive) channel.queueDeclarePassive(queueName)
    else channel.queueDeclare(queueName, false, false, false, null)

    channel
  }
}
