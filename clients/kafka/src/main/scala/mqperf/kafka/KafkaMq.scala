package mqperf.kafka

import com.typesafe.scalalogging.StrictLogging
import mqperf.{Config, Mq, MqReceiver, MqReceiverFactory, MqSender, MqSenderFactory}
import org.apache.kafka.clients.admin.{Admin, NewTopic}
import org.apache.kafka.clients.consumer.{ConsumerRecord, KafkaConsumer, OffsetAndMetadata}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.TopicPartition

import java.time.{Clock, Duration}
import java.util.{Properties, Map => JMap}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise, blocking}
import scala.jdk.CollectionConverters.{MapHasAsJava, SeqHasAsJava}

class KafkaMq(clock: Clock) extends Mq with StrictLogging {
  private val HostsConfigKey = "hosts"
  private val TopicConfigKey = "topic"

  override def init(config: Config): Unit = {
    val hosts: String = config.mqConfig(HostsConfigKey)
    val topic: String = config.mqConfig(TopicConfigKey)
    val partitions: Int = config.mqConfig("partitions").toInt
    val replicationFactor: Short = config.mqConfig("replicationFactor").toShort

    val adminProps = new Properties()
    adminProps.put("bootstrap.servers", hosts)

    try {
      Admin
        .create(adminProps)
        .createTopics(List(new NewTopic(topic, partitions, replicationFactor)).asJava)
        .all()
        .get()

      logger.info(s"Created topic $topic")
    } catch {
      case e: Exception => logger.warn(s"Topic $topic creation failed", e)
    }
  }

  override def cleanUp(config: Config): Unit = {
    val hosts: String = config.mqConfig(HostsConfigKey)
    val topic: String = config.mqConfig(TopicConfigKey)

    val adminProps = new Properties()
    adminProps.put("bootstrap.servers", hosts)

    try {
      Admin
        .create(adminProps)
        .deleteTopics(List(topic).asJava)
        .all()
        .get()

      logger.info(s"Deleted topic $topic")
    } catch {
      case e: Exception => logger.warn(s"Topic $topic deletion failed", e)
    }
  }

  override def createSenderFactory (config: Config): MqSenderFactory = () => new MqSender {
    val hosts: String = config.mqConfig(HostsConfigKey)
    val topic: String = config.mqConfig(TopicConfigKey)
    val acks: String = config.mqConfig("acks")

    val producersProps = new Properties()
    producersProps.put("bootstrap.servers", hosts)
    producersProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    producersProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    producersProps.put("acks", acks)
    val producer = new KafkaProducer[String, String](producersProps)

    override def send(msgs: Seq[String]): Future[Unit] = {
      Future
        .sequence {
          for (msg <- msgs) yield {
            val promise = Promise[Unit]()
            producer.send(
              new ProducerRecord[String, String](topic, msg),
              (_: RecordMetadata, exception: Exception) => if (exception != null) promise.failure(exception) else promise.success(())
            )
            promise.future
          }
        }
        .map(_ => ())
    }
  }

  override def createReceiverFactory(config: Config): MqReceiverFactory = () => new MqReceiver {
    val hosts: String = config.mqConfig(HostsConfigKey)
    val topic: String = config.mqConfig(TopicConfigKey)
    val groupId: String = config.mqConfig("groupId")
    val commitMs: Long = config.mqConfig("commitMs").toLong

    val EmptyOffsetMap = Map.empty[TopicPartition, Long]
    val PollTimeoutMs: Duration = Duration.ofMillis(500)

    val consumerProps = new Properties()
    consumerProps.put("bootstrap.servers", hosts)
    consumerProps.put("group.id", groupId)
    consumerProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    consumerProps.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    consumerProps.put("enable.auto.commit", "false")
    consumerProps.put("auto.offset.reset", "earliest")
    val consumer = new KafkaConsumer[String, String](consumerProps)
    consumer.subscribe(List(topic).asJava)

    override type MsgId = (TopicPartition, Long)

    private def timeToCommit(): Boolean = (clock.millis() - lastCommitTick) > commitMs

    private def commitAsync(): Unit = {
      lastCommitTick = clock.millis()
      if (offsetsToCommit.nonEmpty) {
        logger.info(s"Committing offsets: $offsetsToCommit")
        consumer.commitAsync(
          offsetsToCommit.view.mapValues(new OffsetAndMetadata(_)).toMap.asJava,
          (_: JMap[TopicPartition, OffsetAndMetadata], exception: Exception) => {
            if (exception != null) {
              logger.error("Commit failed", exception)
              throw exception
            }
          }
        )
        offsetsToCommit = EmptyOffsetMap
      }
    }

    private var it: java.util.Iterator[ConsumerRecord[String, String]] = _
    private var offsetsToCommit = EmptyOffsetMap
    private var lastCommitTick = System.nanoTime()

    override def receive(maxMsgCount: Int): Future[Seq[(MsgId, String)]] = Future {
      blocking {
        var msgs: List[(MsgId, String)] = Nil
        if (timeToCommit()) {
          commitAsync()
        }
        if (it == null || !it.hasNext) {
          it = consumer.poll(PollTimeoutMs).iterator()
        }

        while (msgs.size < maxMsgCount && it.hasNext) {
          val msg = it.next()
          val msgId = (new TopicPartition(msg.topic(), msg.partition()), msg.offset())
          msgs = (msgId, msg.value()) :: msgs
        }
        msgs
      }
    }

    override def ack(ids: Seq[MsgId]): Future[Unit] = Future.successful {
      val commitRequest = ids.foldLeft(EmptyOffsetMap) { case (offsetMap, (topicPartition, offset)) =>
        offsetMap + (topicPartition -> (offset + 1))
      }
      offsetsToCommit = offsetsToCommit ++ commitRequest
    }
  }
}
