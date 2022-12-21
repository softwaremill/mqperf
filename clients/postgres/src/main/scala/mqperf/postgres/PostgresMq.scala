package mqperf.postgres

import com.typesafe.scalalogging.StrictLogging
import io.r2dbc.pool.{ConnectionPool, ConnectionPoolConfiguration}
import io.r2dbc.postgresql.client.SSLMode
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
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.jdk.CollectionConverters.{CollectionHasAsScala, IterableHasAsJava}
import scala.jdk.FutureConverters.CompletionStageOps
import scala.util.{Failure, Success}

class PostgresMq(clock: java.time.Clock) extends Mq with StrictLogging {

  private val HostConfigKey = "host"
  private val PortConfigKey = "port"
  private val TableConfigKey = "table"
  private val DatabaseConfigKey = "database"
  private val UsernameConfigKey = "username"
  private val PasswordConfigKey = "password"
  private val SenderPoolSizeConfigKey = "senderPoolSize"
  private val ReceiverPoolSizeConfigKey = "receiverPoolSize"
  private val SslEnabledConfigKey = "sslEnabled"

  override def init(config: Config): Unit = {
    val table = config.mqConfig(TableConfigKey)
    val client = databaseClient(config)

    Await.result(
      client
        .sql(s"create table if not exists $table(id uuid primary key, content text not null, next_delivery timestamptz not null)")
        .fetch()
        .rowsUpdated()
        .flatMap(_ =>
          client
            .sql(s"create index if not exists next_delivery_idx on $table(next_delivery)")
            .fetch()
            .rowsUpdated()
        )
        .toFuture
        .asScala
        .andThen {
          case Success(_)  => logger.info(s"Table $table created.")
          case Failure(ex) => logger.error(s"Table $table creating failure.", ex)
        }
        .map(_ => ()),
      1.minute
    )
  }

  override def cleanUp(config: Config): Unit = {
    val table = config.mqConfig(TableConfigKey)

    Await.result(
      databaseClient(config)
        .sql(s"drop table if exists $table")
        .fetch()
        .rowsUpdated()
        .toFuture
        .asScala
        .andThen {
          case Success(_)  => logger.info(s"Table $table dropped.")
          case Failure(ex) => logger.error(s"Table $table dropping failure.", ex)
        }
        .map(_ => ()),
      1.minute
    )
  }

  override def createSender(config: Config): MqSender = new MqSender {

    private val senderPool = connectionPool(config, config.mqConfig(SenderPoolSizeConfigKey).toInt)
    private val client: DatabaseClient = pooledDatabaseClient(senderPool)
    private val table = config.mqConfig(TableConfigKey)

    override def send(msgs: Seq[String]): Future[Unit] = {
      val query = new StringBuilder(s"insert into $table(id, content, next_delivery) values ")
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

    private val receiverPool: ConnectionPool = connectionPool(config, config.mqConfig(ReceiverPoolSizeConfigKey).toInt)
    private val client: DatabaseClient = pooledDatabaseClient(receiverPool)
    private val txOperator: TransactionalOperator = TransactionalOperator.create(new R2dbcTransactionManager(receiverPool))
    private val table = config.mqConfig(TableConfigKey)

    override def receive(maxMsgCount: Int): Future[Seq[(MsgId, String)]] = {
      val now = ZonedDateTime.now(clock)
      val nextDelivery = now.plusSeconds(100)

      client
        .sql(s"select id, content from $table where next_delivery <= :now for update skip locked limit :limit")
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
              .sql(s"update $table set next_delivery = :nextDelivery where id in (:inIds)")
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
          .sql(s"delete from $table where id in (:inIds)")
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

  private def databaseClient(config: Config): DatabaseClient = {
    DatabaseClient
      .builder()
      .connectionFactory(connectionFactory(config))
      .namedParameters(true)
      .build()
  }

  private def pooledDatabaseClient(connectionPool: ConnectionPool): DatabaseClient = {
    DatabaseClient
      .builder()
      .connectionFactory(connectionPool)
      .namedParameters(true)
      .build()
  }

  private def connectionPool(config: Config, poolSize: Int): ConnectionPool =
    new ConnectionPool(
      ConnectionPoolConfiguration
        .builder(connectionFactory(config))
        .maxIdleTime(Duration.ofMinutes(5))
        .initialSize(poolSize)
        .maxSize(poolSize)
        .minIdle(poolSize)
        .build()
    )

  private def connectionFactory(config: Config): ConnectionFactory =
    new PostgresqlConnectionFactory(
      PostgresqlConnectionConfiguration
        .builder()
        .host(config.mqConfig(HostConfigKey))
        .port(config.mqConfig(PortConfigKey).toInt)
        .username(config.mqConfig(UsernameConfigKey))
        .password(config.mqConfig(PasswordConfigKey))
        .database(config.mqConfig(DatabaseConfigKey))
        .sslMode(if (config.mqConfig(SslEnabledConfigKey).toBoolean) SSLMode.REQUIRE else SSLMode.DISABLE)
        .build()
    )
}
