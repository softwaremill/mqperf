package mqperf

import com.typesafe.scalalogging.StrictLogging

import java.time.Clock
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Receiver(config: Config, mq: Mq, clock: Clock) extends StrictLogging {
  def run(): Future[Unit] = Future { () }
}
