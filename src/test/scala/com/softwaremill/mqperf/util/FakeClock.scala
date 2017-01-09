package com.softwaremill.mqperf.util

import java.util.concurrent.atomic.AtomicLong

import scala.compat.java8.FunctionConverters._
import scala.concurrent.duration._

class FakeClock extends Clock {

  private val currentNanoTime: AtomicLong = new AtomicLong(0L)

  override def nanoTime(): Long = currentNanoTime.get()

  def add(duration: Duration): Unit = currentNanoTime.accumulateAndGet(duration.toNanos, asJavaLongBinaryOperator(_ + _))

}
