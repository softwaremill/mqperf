package com.softwaremill.mqperf.util

trait Clock {
  def nanoTime(): Long

  def currentTimeMillis(): Long
}

object RealClock extends Clock {

  override def nanoTime(): Long = System.nanoTime()

  override def currentTimeMillis(): Long = System.currentTimeMillis()

}
