package mqperf

import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.auto._
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.metrics.prometheus.PrometheusMetrics
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureServerBinding, NettyFutureServerOptions}

import java.net.InetSocketAddress
import java.time.Clock
import java.util.concurrent.atomic.AtomicReference
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

object Server extends StrictLogging {
  Thread.setDefaultUncaughtExceptionHandler((t, e) => { println("Uncaught exception in thread: " + t); e.printStackTrace() })

  private val Sender = "sender"
  private val Receiver = "receiver"

  def start(mq: Mq): Future[NettyFutureServerBinding[InetSocketAddress]] = {
    val inProgress = new AtomicReference[ListBuffer[String]](ListBuffer.empty)

    def initEndpoint(mq: Mq): ServerEndpoint[Any, Future] = infallibleEndpoint.post
      .in("init")
      .in(jsonBody[Config])
      .serverLogicSuccess { config =>
        logger.info(s"Initialising queue with config: $config")
        mq.init(config)
        Future.successful(())
      }

    def cleanUpEndpoint(mq: Mq): ServerEndpoint[Any, Future] = infallibleEndpoint.post
      .in("cleanup")
      .in(jsonBody[Config])
      .serverLogicSuccess { config =>
        logger.info(s"Cleaning up queue with config: $config")
        mq.cleanUp(config)
        Future.successful(())
      }

    def startEndpoint(kind: String, start: Config => Future[Unit]): ServerEndpoint[Any, Future] = infallibleEndpoint.post
      .in("start" / kind)
      .in(jsonBody[Config])
      .out(statusCode(StatusCode.Accepted))
      .serverLogicSuccess { config =>
        logger.info(s"Starting $kind with config: $config")
        inProgress.getAndUpdate(_ += kind)
        start(config).onComplete { result =>
          inProgress.getAndUpdate(_ -= kind)
          result match {
            case Success(_) => logger.info(s"$kind completed")
            case Failure(t) => logger.error(s"$kind failed with config $config", t)
          }
        }
        Future.successful(())
      }

    def inProgressEndpoint: ServerEndpoint[Any, Future] = infallibleEndpoint.get
      .in("in-progress")
      .out(jsonBody[List[String]])
      .serverLogicSuccess { _ => Future.successful(inProgress.get.toList) }

    val prometheusMetrics: PrometheusMetrics[Future] = PrometheusMetrics.default[Future]()
    val metricsEndpoint: ServerEndpoint[Any, Future] = prometheusMetrics.metricsEndpoint

    val allEndpoints: List[ServerEndpoint[Any, Future]] =
      List(
        initEndpoint(mq),
        cleanUpEndpoint(mq),
        startEndpoint(Sender, cfg => new Sender(cfg, mq, Clock.systemUTC()).run()),
        startEndpoint(Receiver, cfg => new Receiver(cfg, mq, Clock.systemUTC()).run()),
        inProgressEndpoint,
        metricsEndpoint
      )

    //

    val serverOptions = NettyFutureServerOptions.customiseInterceptors
      .metricsInterceptor(prometheusMetrics.metricsInterceptor())
      .options
    val host = sys.env.getOrElse("http.host", "0.0.0.0")
    val port = sys.env.get("http.port").map(_.toInt).getOrElse(8080)

    for {
      binding <- NettyFutureServer(serverOptions).host(host).port(port).addEndpoints(allEndpoints).start()
      _ = logger.info(s"Server started at http://${binding.hostName}:${binding.port}")
    } yield binding
  }
}
