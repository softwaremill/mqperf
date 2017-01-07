package com.softwaremill.mqperf.util

trait Clock {
  def nanoTime(): Long
}

object RealClock extends Clock {

  override def nanoTime(): Long = System.nanoTime()

}
