package mqperf.postgres

import com.typesafe.scalalogging.StrictLogging
import io.r2dbc.pool.{ConnectionPool, ConnectionPoolConfiguration}
import io.r2dbc.postgresql.{PostgresqlConnectionConfiguration, PostgresqlConnectionFactory}
import io.r2dbc.spi.{Connection, ConnectionFactory}
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

class PostgresMq(clock: java.time.Clock) extends Mq with StrictLogging {

  private val HostsConfigKey = "hosts"
  private var connectionFactory: Option[ConnectionFactory] = None

  override def init(config: Config): Unit = {
    connectionFactory = Some(
      new PostgresqlConnectionFactory(
        PostgresqlConnectionConfiguration
          .builder()
          .host("postgres")
          .port(5432)
          .username("mquser")
          .password("mqpass")
          .database("mqdb")
          .build()
      )
    )
    connectionFactory.map { cf =>
      Mono
        .usingWhen(
          cf.create(),
          (v: Connection) =>
            Mono
              .from(
                v.createStatement(
                  "CREATE TABLE IF NOT EXISTS jobs(ID UUID PRIMARY KEY, CONTENT TEXT NOT NULL, NEXT_DELIVERY TIMESTAMPTZ NOT NULL)"
                ).execute()
              )
              .map(v => {
                logger.info(s"Result $v")
                v
              })
              .onErrorResume(ex => {
                logger.error("Error", ex)
                Mono.empty()
              }),
          (v: Connection) => v.close()
        )
        .onErrorResume(ex => {
          logger.error("Error", ex)
          Mono.empty()
        })
        .subscribe()
    }
  }

  override def cleanUp(config: Config): Unit = ???

  override def createSender(config: Config): MqSender = new MqSender {

    private val senderPool: ConnectionPool = connectionFactory
      .map(cf =>
        new ConnectionPool(
          ConnectionPoolConfiguration
            .builder(cf)
            .maxIdleTime(Duration.ofMillis(1000))
            .maxSize(10)
            .minIdle(10)
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

    private val receiverPool: ConnectionPool = connectionFactory
      .map(cf =>
        new ConnectionPool(
          ConnectionPoolConfiguration
            .builder(cf)
            .maxIdleTime(Duration.ofMillis(1000))
            .maxSize(10)
            .minIdle(10)
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

    override def ack(ids: Seq[MsgId]): Future[Unit] =
      client
        .sql("delete from jobs where id in (:inIds)")
        .bind("inIds", ids.asJava)
        .fetch()
        .rowsUpdated()
        .toFuture
        .asScala
        .map(_ => ())

    override def close(): Future[Unit] = {
      receiverPool
        .disposeLater()
        .toFuture
        .asScala
        .map(_ => ())
    }
  }
}
