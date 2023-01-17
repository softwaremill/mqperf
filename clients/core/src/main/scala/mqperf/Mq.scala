package mqperf

import scala.concurrent.Future

trait Mq {
  def init(config: Config): Unit
  def cleanUp(config: Config): Unit

  /** returns new instance of MqSenderFactory each time */
  def createSenderFactory(config: Config): MqSenderFactory

  /** returns new instance of MqReceiverFactory each time */
  def createReceiverFactory(config: Config): MqReceiverFactory
}

trait MqSenderFactory {
  def createSender(): MqSender

  /** close resources which are shared between senders within one factory */
  def close(): Future[Unit] = Future.successful(())

}

trait MqSender {
  def send(msgs: Seq[String]): Future[Unit]
  def close(): Future[Unit] = Future.successful(())
}

trait MqReceiverFactory {
  def createReceiver(): MqReceiver

  /** close resources which are shared between receivers within one factory */
  def close(): Future[Unit] = Future.successful(())
}

trait MqReceiver {
  type MsgId
  def receive(maxMsgCount: Int): Future[Seq[(MsgId, String)]]
  def ack(ids: Seq[MsgId]): Future[Unit]
  def close(): Future[Unit] = Future.successful(())
}
