package mqperf.rabbitmq

import com.rabbitmq.client._
import com.rabbitmq.client.impl.nio.NioParams
import com.typesafe.scalalogging.StrictLogging
import mqperf.{Config, Mq, MqReceiver, MqReceiverFactory, MqSender, MqSenderFactory}

import java.util.concurrent.{ConcurrentLinkedQueue, LinkedBlockingQueue}
import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, blocking}
import scala.util.{Failure, Success, Try}

class RabbitMq extends Mq with StrictLogging {
  private val HostsConfigKey = "hosts"
  private val QueueNameConfigKey = "queueName"
  private val MaxPollAttemptsConfigKey = "maxPollAttempts"
  private val UsernameConfigKey = "username"
  private val PasswordConfigKey = "password"
  private val NbIoThreadsConfigKey = "nbNioThreads"
  private val multipleAckConfigKey = "multipleAck"

  override def createSenderFactory(config: Config): MqSenderFactory = new MqSenderFactory {
    private val senderFactoryConnection: Connection = createConnection(config)

    override def createSender(): MqSender = new MqSender {
      private val channelPool: LinkedBlockingQueue[Channel] = new LinkedBlockingQueue[Channel]()
      private val queueName: String = config.mqConfig(QueueNameConfigKey)

      for (_ <- 1 to config.senderConcurrency) {
        val channel = newChannel(senderFactoryConnection, queueName, passive = true)
        channel.confirmSelect()
        channelPool.add(channel)
      }

      /*
          Send method uses channels pool in order to be able to send messages using the sender instance on multiple threads concurrently.
          Number of channels is determined by the senderConcurrency value which is also the amount of sender threads.
          If all of the available channels are used at a specific time for sending the other threads will actively wait for the channels to be available
          in the pool again. Only after a successful channel poll will they be able to publish more messages.

          In theory while testing RabbitMQ, we should manipulate the sendersNumber param which changes the number of MqSender instances and the senderConcurrency param
          should equal 1. However, it is also possible to set it to a higher number and use the channel pooling solution.
       */
      override def send(msgs: Seq[String]): Future[Unit] = Future {
        blocking {
          val channel = channelPool.take()
          for (msg <- msgs) {
            channel.basicPublish("", queueName, MessageProperties.PERSISTENT_TEXT_PLAIN, msg.getBytes)
          }

          channel.waitForConfirms()
          channelPool.add(channel)
          ()
        }
      }
    }

    override def close(): Future[Unit] = Future(senderFactoryConnection.close())
  }

  override def createReceiverFactory(config: Config): MqReceiverFactory = new MqReceiverFactory {
    private val receiverFactoryConnection: Connection = createConnection(config)

    override def createReceiver(): MqReceiver = new MqReceiver {
      override type MsgId = Long

      private val queueName: String = config.mqConfig(QueueNameConfigKey)
      private val maxPollAttempts: Int = Option(config.mqConfig(MaxPollAttemptsConfigKey)).map(_.toInt).getOrElse(0)
      private val queue = new ConcurrentLinkedQueue[(MsgId, String)]()

      private val channel = newChannel(receiverFactoryConnection, queueName, passive = true)
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

    override def close(): Future[Unit] = Future(receiverFactoryConnection.close())
  }

  override def init(config: Config): Unit = {
    val queueName: String = config.mqConfig(QueueNameConfigKey)

    Option(createConnection(config))
      .foreach(conn => {
        newChannel(conn, queueName, passive = false)
        logger.info(s"Created queue: $queueName")
        conn.close()
      })
  }

  override def cleanUp(config: Config): Unit = {
    val queueName: String = config.mqConfig(QueueNameConfigKey)

    val connection = createConnection(config)
    Try(newChannel(connection, queueName, passive = true)) match {
      case Success(channel) =>
        channel.queuePurge(queueName)
        channel.queueDelete(queueName)
        logger.info(s"Deleted queue $queueName")
      case Failure(_) => logger.info(s"Queue $queueName does not exist - nothing to delete")
    }

    connection.close()
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

//    TODO: needs to be tested more thoroughly in order to decide if the results are better than with blocking-io
    cf.useNio()
    cf.setNioParams(new NioParams().setNbIoThreads(nbNioThreads))

    val connection = cf.newConnection()
    connection
  }

  private def newChannel(conn: Connection, queueName: String, passive: Boolean): Channel = {
    val channel = conn.createChannel()
    if (passive) channel.queueDeclarePassive(queueName)
    else channel.queueDeclare(queueName, false, false, false, null)
    channel
  }
}
