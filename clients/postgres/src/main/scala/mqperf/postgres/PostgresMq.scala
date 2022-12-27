package mqperf.postgres

import cats.effect._
import cats.effect.unsafe.IORuntime
import cats.implicits.catsSyntaxApplicativeId
import cats.syntax.seq._
import com.typesafe.scalalogging.StrictLogging
import com.zaxxer.hikari.HikariConfig
import doobie.hikari.HikariTransactor
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment.const
import doobie.util.fragments.in
import doobie._
import mqperf.{Config, Mq, MqReceiver, MqSender}

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class PostgresMq(clock: java.time.Clock) extends Mq with StrictLogging {

  implicit val ioRuntime: IORuntime = cats.effect.unsafe.IORuntime.global
  implicit val exContext: ExecutionContext = scala.concurrent.ExecutionContext.global

  private val HostConfigKey = "host"
  private val PortConfigKey = "port"
  private val TableConfigKey = "table"
  private val DatabaseConfigKey = "database"
  private val UsernameConfigKey = "username"
  private val PasswordConfigKey = "password"

  override def init(config: Config): Unit = {
    val table = config.mqConfig(TableConfigKey)
    val tx = transactor(config)

    val initQueries: ConnectionIO[Int] = for {
      _ <- (
        fr"create table if not exists"
          ++ const(table)
          ++ fr"(id uuid primary key, content text not null, next_delivery timestamptz not null)"
      ).update.run
      v <- (
        fr"create index if not exists next_delivery_idx on "
          ++ const(table)
          ++ fr"(next_delivery)"
      ).update.run
    } yield v

    initQueries
      .transact(tx)
      .unsafeRunSync()

    logger.info(s"Table $table created.")
  }

  override def cleanUp(config: Config): Unit = {
    val table = config.mqConfig(TableConfigKey)
    val tx = transactor(config)

    (sql"drop table if exists " ++ const(table)).update.run
      .transact(tx)
      .unsafeRunSync()

    logger.info(s"Table $table dropped.")
  }

  override def createSender(config: Config): MqSender = new MqSender {

    private val table = config.mqConfig(TableConfigKey)
    private val (tx, closure) = pooledTransactor(config, config.senderConcurrency).allocated.unsafeRunSync()

    override def send(msgs: Seq[String]): Future[Unit] = {

      val insertRecords = msgs.map(m => (UUID.randomUUID(), m, OffsetDateTime.now()))
      val insertQuery = s"insert into $table(id, content, next_delivery) values (?, ?, ?)"

      Update[(UUID, String, OffsetDateTime)](insertQuery)
        .updateMany(insertRecords)
        .transact(tx)
        .unsafeToFuture()
        .map(_ => ())
    }

    override def close(): Future[Unit] = {
      logger.info("Closing sender connection pool")
      closure.unsafeToFuture()
    }
  }

  override def createReceiver(config: Config): MqReceiver = new MqReceiver {

    override type MsgId = UUID

    private val table = config.mqConfig(TableConfigKey)
    private val (tx, closure) = pooledTransactor(config, config.receiverConcurrency).allocated.unsafeRunSync()

    override def receive(maxMsgCount: Int): Future[Seq[(MsgId, String)]] = {
      val now = OffsetDateTime.now(clock)
      val nextDelivery = now.plusSeconds(100)
      val receiveQueries = for {
        msgs <- (
          fr"select id, content from "
            ++ const(table)
            ++ fr"where next_delivery <= $now"
            ++ fr"limit $maxMsgCount"
            ++ fr"for update skip locked"
        ).query[(MsgId, String)].to[Seq]
        _ <- msgs.toNeSeq
          .map(nes =>
            (
              fr"update"
                ++ const(table)
                ++ fr"set next_delivery = $nextDelivery"
                ++ fr"where"
                ++ in(fr"id", nes.map(_._1))
            ).update.run
          )
          .getOrElse(0.pure[ConnectionIO])
      } yield msgs

      receiveQueries
        .transact(tx)
        .unsafeToFuture()
    }

    override def ack(ids: Seq[MsgId]): Future[Unit] = {
      ids.toNeSeq
        .map(nes =>
          (
            fr"delete from"
              ++ const(table)
              ++ fr"where"
              ++ in(fr"id", nes)
          ).update.run
            .transact(tx)
            .unsafeToFuture()
            .map(_ => ())
        )
        .getOrElse(Future.successful(()))
    }

    override def close(): Future[Unit] = {
      logger.info("Closing receiver connection pool")
      closure.unsafeToFuture()
    }
  }

  private def transactor(config: Config): Transactor[IO] =
    Transactor.fromDriverManager[IO](
      "org.postgresql.Driver",
      jdbcUrl(config),
      config.mqConfig(UsernameConfigKey),
      config.mqConfig(PasswordConfigKey)
    )
  private def pooledTransactor(config: Config, poolSize: Int): Resource[IO, HikariTransactor[IO]] = {
    for {
      connectEC <- doobie.util.ExecutionContexts.fixedThreadPool[IO](poolSize)
      tx <- HikariTransactor.fromHikariConfig[IO](
        {
          val hc = new HikariConfig()
          hc.setUsername(config.mqConfig(UsernameConfigKey))
          hc.setPassword(config.mqConfig(PasswordConfigKey))
          hc.setJdbcUrl(jdbcUrl(config))
          hc.setMinimumIdle(poolSize)
          hc.setMaximumPoolSize(poolSize)
          hc.setMaxLifetime(5.minutes.toMillis)
          hc
        },
        connectEC
      )
    } yield tx
  }
  private def jdbcUrl(config: Config): String =
    s"jdbc:postgresql://${config.mqConfig(HostConfigKey)}:${config.mqConfig(PortConfigKey)}/${config.mqConfig(DatabaseConfigKey)}"

}
