package mqperf

import scala.concurrent.Future

trait Mq {
  def init(config: Config): Unit
  def cleanUp(config: Config): Unit

  //TODO: to discuss: should this function for multiple calls return the same instance of `MqSenderFactory`?
  def senderFactory(config: Config): MqSenderFactory = ???

  @Deprecated
  def createSender(config: Config): MqSender
  def createReceiver(config: Config): MqReceiver
}

trait MqSenderFactory {
  def createSender(): MqSender
}

trait MqSender {
  def send(msgs: Seq[String]): Future[Unit]
  def close(): Future[Unit] = Future.successful(())
}

trait MqReceiver {
  type MsgId
  def receive(maxMsgCount: Int): Future[Seq[(MsgId, String)]]
  def ack(ids: Seq[MsgId]): Future[Unit]
  def close(): Future[Unit] = Future.successful(())
}
