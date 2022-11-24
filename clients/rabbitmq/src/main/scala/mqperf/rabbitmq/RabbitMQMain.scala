package mqperf.rabbitmq

import mqperf.Server

object RabbitMQMain extends App {
  Server.start(new RabbitMq())
}
