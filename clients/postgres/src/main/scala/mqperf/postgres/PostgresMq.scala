package mqperf.postgres

import cats.effect._
import cats.effect.unsafe.IORuntime
import com.typesafe.scalalogging.StrictLogging
import com.zaxxer.hikari.HikariConfig
import doobie._
import doobie.hikari.HikariTransactor
import doobie.implicits._
import mqperf.{Config, Mq, MqReceiver, MqSender}

import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class PostgresMq(clock: java.time.Clock) extends Mq with StrictLogging {

  implicit val ioRuntime: IORuntime = cats.effect.unsafe.IORuntime.global

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
      _ <- (sql"create table if not exists " ++ Fragment.const(table) ++ sql"(id uuid primary key, content text not null, next_delivery timestamptz not null)").update.run
      v <- (sql"create index if not exists next_delivery_idx on " ++ Fragment.const(table) ++ sql"(next_delivery)").update.run
    } yield v

    initQueries
      .transact(tx)
      .unsafeRunSync()

    logger.info(s"Table $table created.")
  }


  override def cleanUp(config: Config): Unit = {
    val table = config.mqConfig(TableConfigKey)
    val tx = transactor(config)

    (sql"drop table if exists " ++ Fragment.const(table)).update.run
      .transact(tx)
      .unsafeRunSync()

    logger.info(s"Table $table dropped.")
  }

  override def createSender(config: Config): MqSender = new MqSender {

//    private val senderPool = connectionPool(config, config.senderConcurrency)
//    private val client: DatabaseClient = pooledDatabaseClient(senderPool)
    private val table = config.mqConfig(TableConfigKey)

    override def send(msgs: Seq[String]): Future[Unit] = {

      Future.successful(())
//      val query = new StringBuilder(s"insert into $table(id, content, next_delivery) values ")
//      query ++= s"('${UUID.randomUUID()}', '${msgs.head}', '${ZonedDateTime.now(clock)}')"
//      msgs.tail.foreach(m => query ++= s", ('${UUID.randomUUID()}', '$m', '${ZonedDateTime.now(clock)}')")
//
//      client
//        .sql(query.toString)
//        .fetch()
//        .rowsUpdated()
//        .toFuture
//        .asScala
//        .map(_ => ())
    }

    override def close(): Future[Unit] =
      Future.successful(())
//      senderPool
//        .disposeLater()
//        .toFuture
//        .asScala
//        .map(_ => ())
  }

  override def createReceiver(config: Config): MqReceiver = new MqReceiver {

    override type MsgId = UUID

//    private val receiverPool: ConnectionPool = connectionPool(config, config.receiverConcurrency)
//    private val client: DatabaseClient = pooledDatabaseClient(receiverPool)
//    private val txOperator: TransactionalOperator = TransactionalOperator.create(new R2dbcTransactionManager(receiverPool))
//    private val table = config.mqConfig(TableConfigKey)
//
//    private val selectQuery = client
//      .sql(s"select id, content from $table where next_delivery <= :now limit :limit for update skip locked")
//    private val updateQuery = client
//      .sql(s"update $table set next_delivery = :nextDelivery where id in (:inIds)")
//    private val deleteQuery = client
//      .sql(s"delete from $table where id in (:inIds)")

    override def receive(maxMsgCount: Int): Future[Seq[(MsgId, String)]] = {
      Future.successful(Seq())
//      val now = ZonedDateTime.now(clock)
//      val nextDelivery = now.plusSeconds(100)
//
//      selectQuery
//        .bind("now", now)
//        .bind("limit", maxMsgCount)
//        .map(row => (row.get("id", classOf[UUID]), row.get("content", classOf[String])))
//        .all()
//        .collectList()
//        .map(_.asScala.toList)
//        .flatMap {
//          case Nil =>
//            Mono.just(Seq.empty[(MsgId, String)])
//          case ids =>
//            updateQuery
//              .bind("nextDelivery", nextDelivery)
//              .bind("inIds", ids.map(_._1).asJava)
//              .fetch()
//              .rowsUpdated()
//              .map(_ => ids)
//        }
//        .as((v: Mono[Seq[(MsgId, String)]]) => txOperator.transactional(v))
//        .toFuture
//        .asScala
    }

    override def ack(ids: Seq[MsgId]): Future[Unit] =
      Future.successful(())
//      ids match {
//      case Nil =>
//        Future.successful(())
//      case vs =>
//        deleteQuery
//          .bind("inIds", vs.asJava)
//          .fetch()
//          .rowsUpdated()
//          .toFuture
//          .asScala
//          .map(_ => ())
//    }

    override def close(): Future[Unit] = {
      Future.successful(())
//      receiverPool
//        .disposeLater()
//        .toFuture
//        .asScala
//        .map(_ => ())
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
          hc.setDataSourceClassName("org.postgresql.Driver")
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
