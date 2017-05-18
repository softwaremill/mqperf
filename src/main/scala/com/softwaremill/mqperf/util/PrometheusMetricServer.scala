package com.softwaremill.mqperf.util

import java.io.StringWriter

import akka.actor.{ActorSystem, Terminated}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpCharsets, HttpEntity, MediaTypes}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat

import scala.concurrent.Future
import scala.util.Failure

object PrometheusMetricServer extends StrictLogging {
  def start(registry: CollectorRegistry, interface: String, port: Int): Future[() => Future[Terminated]] = {
    implicit val system = ActorSystem("prometheus")
    implicit val materializer = ActorMaterializer()
    import system.dispatcher

    val contentType = MediaTypes.`text/plain`.withParams(Map("version" -> "0.0.4")).withCharset(HttpCharsets.`UTF-8`)

    val routes = get {
      path("metrics") {
        complete(HttpEntity(contentType, {
          val writer = new StringWriter()
          TextFormat.write004(writer, registry.metricFamilySamples())
          writer.toString
        }))
      }
    }
    
    Http().bindAndHandle(routes, interface, port).map { sb =>
      () => sb.unbind().flatMap(_ => system.terminate()).andThen {
        case Failure(e) => logger.error("Cannot stop metrics export", e)
      }
    }.andThen {
      case Failure(e) => logger.error("Cannot start metrics export", e)
    }
  }
}
