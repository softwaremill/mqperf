package mqperf.postgres

import mqperf.{Config, Mq, MqReceiver, MqSender}

import scala.concurrent.Future

class PostgresMq(clock: java.time.Clock) extends Mq {
  override def init(config: Config): Unit = ???

  override def cleanUp(config: Config): Unit = ???

  override def createSender(config: Config): MqSender = new MqSender {
    override def send(msgs: Seq[String]): Future[Unit] = ???
  }

  override def createReceiver(config: Config): MqReceiver = new MqReceiver {

    override def receive(maxMsgCount: Int): Future[Seq[(MsgId, String)]] = ???

    override def ack(ids: Seq[MsgId]): Future[Unit] = ???
  }
}
