package com.softwaremill.mqperf

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor2}

class RandomMessagesPoolTest extends AnyFlatSpec with TableDrivenPropertyChecks with Matchers {
  val invalidParameters: TableFor2[Int, Int] = Table(
    ("messageLength", "poolSize"),
    (-2, -3),
    (-1, 2),
    (1, -2),
    (0, 0),
    (1, 0),
    (0, 1)
  )

  forAll(invalidParameters) { (messageLength: Int, poolSize: Int) =>
    it should s"report an error on invalid parameters $messageLength/$poolSize" in {
      val caught = intercept[IllegalArgumentException] {
        RandomMessagesPool(messageLength, poolSize)
      }

      caught.getMessage should startWith("Invalid message pool parameters")
    }
  }

  val validParameters: TableFor2[Int, Int] = Table(
    ("messageLength", "poolSize"),
    (1, 1),
    (1, 4),
    (4, 1),
    (3, 7),
    (16, 27),
    (100, 10000)
  )

  forAll(validParameters) { (messageLength: Int, poolSize: Int) =>
    it should s"provide circular iteration messages of length $messageLength over a pool of size $poolSize" in {
      val messagesPool = RandomMessagesPool(messageLength, poolSize)
      val messages = (1 to poolSize).map(_ => messagesPool.nextMessage())
      val messages2 = (1 to poolSize).map(_ => messagesPool.nextMessage())
      
      messages.size should equal(messages2.size)
      messages.zip(messages2).count(mPair => mPair._1.equals(mPair._2)) should equal(messages.size)
    }
  }
}
