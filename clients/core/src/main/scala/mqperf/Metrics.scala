package mqperf

import io.prometheus.metrics.core.metrics.{Counter, Histogram}

object Metrics {
  val testIdLabelName = "testId"

  object Sender {
    val messageCounter: Counter =
      Counter
        .builder()
        .name("mqperf_sent")
        .help("total number of sent messages since start of application")
        .labelNames(testIdLabelName)
        .register()

    val messageLatencyHistogram: Histogram = Histogram
      .builder()
      .name("mqperf_send_latency_ms")
      .help("latency of sent messages")
      // TODO: use the new API
      .classicUpperBounds(0, 50, 100, 150, 200, 250, 300, 350, 400, 450, 500, 600, 700, 800, 900, 1000, 1250, 1500, 1750, 2000, 2500, 3000,
        3500, 4000, 4500, 5000)
      .labelNames(testIdLabelName)
      .register()
  }

  object Receiver {
    val messageCounter: Counter = Counter
      .builder()
      .name("mqperf_received")
      .help("total number of received messages since start of application")
      .labelNames(testIdLabelName)
      .register()

    val messageLatencyHistogram: Histogram = Histogram
      .builder()
      .name("mqperf_receive_latency_ms")
      .help("latency of received messages")
      // TODO: use the new API
      .classicUpperBounds(0, 50, 100, 150, 200, 250, 300, 350, 400, 450, 500, 600, 700, 800, 900, 1000, 1250, 1500, 1750, 2000, 2500, 3000,
        3500, 4000, 4500, 5000, 6000, 7000, 8000, 9000, 10000, 20000, 30000, 40000, 50000, 60000)
      .labelNames(testIdLabelName)
      .register()
  }
}
