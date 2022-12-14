package mqperf.postgres

import com.typesafe.scalalogging.StrictLogging
import io.r2dbc.pool.{ConnectionPool, ConnectionPoolConfiguration}
import io.r2dbc.postgresql.{PostgresqlConnectionConfiguration, PostgresqlConnectionFactory}
import io.r2dbc.spi.ConnectionFactory
import mqperf.{Config, Mq, MqReceiver, MqSender}
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Mono

import java.time.{Duration, ZonedDateTime}
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.jdk.CollectionConverters.{CollectionHasAsScala, IterableHasAsJava}
import scala.jdk.FutureConverters.CompletionStageOps
import scala.util.{Failure, Success}

class PostgresMq(clock: java.time.Clock) extends Mq with StrictLogging {

  private val MqUser = "mquser"
  private val MqPass = "mqpass"
  private val MqDb = "mqdb"

  private val HostConfigKey = "host"
  private val PortConfigKey = "port"
  private val TableConfigKey = "table"
  private val SenderPoolSize = "senderPoolSize"
  private val ReceiverPoolSize = "receiverPoolSize"

  private var connectionFactory: Option[ConnectionFactory] = None

  override def init(config: Config): Unit = {
    val host = config.mqConfig(HostConfigKey)
    val port = config.mqConfig(PortConfigKey).toInt
    val table = config.mqConfig(TableConfigKey)

    connectionFactory = Some(
      new PostgresqlConnectionFactory(
        PostgresqlConnectionConfiguration
          .builder()
          .host(host)
          .port(port)
          .username(MqUser)
          .password(MqPass)
          .database(MqDb)
          .build()
      )
    )

    connectionFactory.foreach(cf => {
      val client: DatabaseClient = DatabaseClient
        .builder()
        .connectionFactory(cf)
        .namedParameters(true)
        .build()

      client
        .sql(s"create table if not exists $table(id uuid primary key, content text not null, next_delivery timestamptz not null)")
        .fetch()
        .rowsUpdated()
        .flatMap(_ =>
          client
            .sql("create index if not exists next_delivery_idx on jobs(next_delivery)")
            .fetch()
            .rowsUpdated()
        )
        .toFuture
        .asScala
        .andThen {
          case Success(_)  => logger.info(s"Table $table created.")
          case Failure(ex) => logger.error(s"Table $table creating failure.", ex)
        }
    })
  }

  override def cleanUp(config: Config): Unit = connectionFactory.foreach(cf => {
    val table = config.mqConfig(TableConfigKey)

    val client: DatabaseClient = DatabaseClient
      .builder()
      .connectionFactory(cf)
      .namedParameters(true)
      .build()

    client
      .sql(s"drop table if exists $table")
      .fetch()
      .rowsUpdated()
      .toFuture
      .asScala
      .andThen {
        case Success(_)  => logger.info(s"Table $table dropped.")
        case Failure(ex) => logger.error(s"Table $table dropping failure.", ex)
      }
  })

  override def createSender(config: Config): MqSender = new MqSender {

    private val poolSize = config.mqConfig(SenderPoolSize).toInt

    private val senderPool: ConnectionPool = connectionFactory
      .map(cf =>
        new ConnectionPool(
          ConnectionPoolConfiguration
            .builder(cf)
            .maxIdleTime(Duration.ofMinutes(5))
            .initialSize(poolSize)
            .maxSize(poolSize)
            .minIdle(poolSize)
            .build()
        )
      )
      .getOrElse(throw new RuntimeException("Did you initialize PostgresMq using /init endpoint?"))

    private val client: DatabaseClient = DatabaseClient
      .builder()
      .connectionFactory(senderPool)
      .namedParameters(true)
      .build()

    override def send(msgs: Seq[String]): Future[Unit] = {
      val query = new StringBuilder("insert into jobs(id, content, next_delivery) values ")
      query ++= s"('${UUID.randomUUID()}', '${msgs.head}', '${ZonedDateTime.now(clock)}')"
      msgs.tail.foreach(m => query ++= s", ('${UUID.randomUUID()}', '$m', '${ZonedDateTime.now(clock)}')")

      client
        .sql(query.toString)
        .fetch()
        .rowsUpdated()
        .toFuture
        .asScala
        .map(_ => ())
    }

    override def close(): Future[Unit] =
      senderPool
        .disposeLater()
        .toFuture
        .asScala
        .map(_ => ())
  }

  override def createReceiver(config: Config): MqReceiver = new MqReceiver {

    override type MsgId = UUID

    private val poolSize = config.mqConfig(ReceiverPoolSize).toInt

    private val receiverPool: ConnectionPool = connectionFactory
      .map(cf =>
        new ConnectionPool(
          ConnectionPoolConfiguration
            .builder(cf)
            .maxIdleTime(Duration.ofMinutes(5))
            .initialSize(poolSize)
            .maxSize(poolSize)
            .minIdle(poolSize)
            .build()
        )
      )
      .getOrElse(throw new RuntimeException("Did you initialize PostgresMq using /init endpoint?"))

    private val client: DatabaseClient = DatabaseClient
      .builder()
      .connectionFactory(receiverPool)
      .namedParameters(true)
      .build()

    private val txOperator: TransactionalOperator = TransactionalOperator.create(new R2dbcTransactionManager(receiverPool))

    override def receive(maxMsgCount: Int): Future[Seq[(MsgId, String)]] = {
      val now = ZonedDateTime.now(clock)
      val nextDelivery = now.plusSeconds(100)

      client
        .sql("select id, content from jobs where next_delivery <= :now for update skip locked limit :limit")
        .bind("now", now)
        .bind("limit", maxMsgCount)
        .map(row => (row.get("id", classOf[UUID]), row.get("content", classOf[String])))
        .all()
        .collectList()
        .map(_.asScala.toList)
        .flatMap {
          case Nil =>
            Mono.just(Seq.empty[(MsgId, String)])
          case ids =>
            client
              .sql("update jobs set next_delivery = :nextDelivery where id in (:inIds)")
              .bind("nextDelivery", nextDelivery)
              .bind("inIds", ids.map(_._1).asJava)
              .fetch()
              .rowsUpdated()
              .map(_ => ids)
        }
        .as((v: Mono[Seq[(MsgId, String)]]) => txOperator.transactional(v))
        .toFuture
        .asScala
    }

    override def ack(ids: Seq[MsgId]): Future[Unit] = ids match {
      case Nil =>
        Future.successful(())
      case vs =>
        client
          .sql("delete from jobs where id in (:inIds)")
          .bind("inIds", vs.asJava)
          .fetch()
          .rowsUpdated()
          .toFuture
          .asScala
          .map(_ => ())
    }


    override def close(): Future[Unit] = {
      receiverPool
        .disposeLater()
        .toFuture
        .asScala
        .map(_ => ())
    }
  }
}
