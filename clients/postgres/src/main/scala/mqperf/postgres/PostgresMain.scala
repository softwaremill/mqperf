package mqperf.postgres

import mqperf.Server

import java.time.Clock

object PostgresMain extends App {
  Server.start(new PostgresMq(Clock.systemUTC()))
}
