package mqperf.postgres

import com.typesafe.scalalogging.StrictLogging
import io.r2dbc.pool.{ConnectionPool, ConnectionPoolConfiguration}
import io.r2dbc.postgresql.{PostgresqlConnectionConfiguration, PostgresqlConnectionFactory}
import io.r2dbc.spi.Connection
import mqperf.{Config, Mq, MqReceiver, MqSender}
import reactor.core.publisher.Mono

import java.time.Duration
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class PostgresMq(clock: java.time.Clock) extends Mq with StrictLogging {

  private val HostsConfigKey = "hosts"

  override def init(config: Config): Unit = {
    Try {
      val connectionFactory = new PostgresqlConnectionFactory(
        PostgresqlConnectionConfiguration
          .builder()
          .host("postgres")
          .port(5432)
          .username("mquser")
          .password("mqpass")
          .database("mqdb")
          .build()
      )

      val poolConfiguration = ConnectionPoolConfiguration
        .builder(connectionFactory)
        .maxIdleTime(Duration.ofMillis(1000))
        .maxSize(10)
        .minIdle(10)
        .build()

      val connectionPool = new ConnectionPool(poolConfiguration)

      Mono
        .usingWhen(
          connectionPool.create(),
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
        .thenEmpty(connectionPool.close())
        .subscribe()
    } match {
      case Success(v)  => logger.info(s"PostgresMq initialized $v")
      case Failure(ex) => logger.error("PostgresMq initialization error", ex)
    }
  }

  override def cleanUp(config: Config): Unit = ???

  override def createSender(config: Config): MqSender = new MqSender {
    override def send(msgs: Seq[String]): Future[Unit] = ???
  }

  override def createReceiver(config: Config): MqReceiver = new MqReceiver {

    override def receive(maxMsgCount: Int): Future[Seq[(MsgId, String)]] = ???

    override def ack(ids: Seq[MsgId]): Future[Unit] = ???
  }
}
