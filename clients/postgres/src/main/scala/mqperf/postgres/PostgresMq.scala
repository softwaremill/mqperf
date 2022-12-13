package mqperf.postgres

import com.typesafe.scalalogging.StrictLogging
import io.r2dbc.pool.{ConnectionPool, ConnectionPoolConfiguration}
import io.r2dbc.postgresql.{PostgresqlConnectionConfiguration, PostgresqlConnectionFactory}
import io.r2dbc.spi.{Connection, ConnectionFactory, Result}
import mqperf.{Config, Mq, MqReceiver, MqSender}
import org.reactivestreams.Subscription
import reactor.core.CoreSubscriber
import reactor.core.publisher.{BaseSubscriber, Flux, Mono}

import java.time.{Duration, OffsetDateTime, ZonedDateTime}
import java.util.UUID
import java.util.function.Consumer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.jdk.FutureConverters.CompletionStageOps
import scala.util.{Failure, Success}

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

    val senderPool: ConnectionPool = connectionFactory
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

    override def send(msgs: Seq[String]): Future[Unit] = {
      Mono
        .usingWhen(
          senderPool.create(),
          (c: Connection) =>
            Mono.from {
              val insert = s"insert into jobs(id, content, next_delivery) values "
              val values = msgs
                .map(msg => {
                  s"('${UUID.randomUUID()}', '$msg', '${OffsetDateTime.now(clock)}')"
                })
                .mkString(",")
              c.createStatement(s"$insert $values").execute()
            },
          (c: Connection) => c.close
        )
        .toFuture
        .asScala
        .flatMap(r => Mono.from(r.getRowsUpdated).toFuture.asScala)
        .andThen {
          case Success(v)  => logger.info(s"Sender#send done, inserted rows: $v")
          case Failure(ex) => logger.error(s"Sender#send fails", ex)
        }
        .map(_ => ())
    }

    override def close(): Future[Unit] =
      senderPool
        .disposeLater()
        .toFuture
        .asScala
        .map(_ => ())
        .andThen {
          case Success(v)  => logger.info(s"PostgresMq#close done with $v")
          case Failure(ex) => logger.error(s"PostgresMq#close fails", ex)
        }
  }

  override def createReceiver(config: Config): MqReceiver = new MqReceiver {

    override type MsgId = String

    val receiverPool: ConnectionPool = connectionFactory
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

    override def receive(maxMsgCount: Int): Future[Seq[(MsgId, String)]] = {
      val now = ZonedDateTime.now(clock)
      val nextDelivery = now.plusSeconds(100)

      val promise = Promise[Seq[(MsgId, String)]]
      Flux.usingWhen(
        receiverPool.create(),
        (c: Connection) =>
          c.createStatement("select id, content from jobs where next_delivery <= $1")
            .bind("$1", now)
            .execute()
        ,
        (c: Connection) => c.close()
      )
      .doOnComplete(() => promise.success(Seq()))
      .doOnError(ex => promise.failure(ex))
      .flatMap((r: Result) => r.map(_.get("id", classOf[UUID])))
      .subscribe((t: UUID) => logger.info(s"ID: $t"))
      promise.future
    }


    override def ack(ids: Seq[MsgId]): Future[Unit] = Future.successful()
  }
}
