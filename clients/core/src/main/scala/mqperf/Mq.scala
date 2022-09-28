package mqperf

import scala.concurrent.Future

trait Mq {
  def createSender(config: Config): MqSender
  def createReceiver(config: Config): MqReceiver
}

trait MqSender {
  def send(msgs: Seq[String]): Future[Unit]
  def close(): Future[Unit] = Future.successful(())
}

trait MqReceiver {
  type MsgId
  def receive(maxMsgCount: Int): Seq[(MsgId, String)]
  def ack(ids: List[MsgId]): Future[Unit]
  def close(): Future[Unit] = Future.successful(())
}
