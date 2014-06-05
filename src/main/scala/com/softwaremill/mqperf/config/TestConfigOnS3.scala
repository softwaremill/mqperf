package com.softwaremill.mqperf.config

import com.amazonaws.services.s3.AmazonS3Client
import scala.io.Source
import scala.collection.JavaConversions._
import java.io.File
import com.amazonaws.services.s3.model.{CannedAccessControlList, PutObjectRequest}

class TestConfigOnS3 {
  private val s3Client = new AmazonS3Client(AWSCredentialsFromEnv())
  private val bucketName = "mqperf"
  private val objectName = "test_config.json"
  private val whenChangedPollEveryMs = 1000L

  def read(): String = {
    val exists = s3Client
      .listObjects(bucketName, objectName)
      .getObjectSummaries
      .filter(_.getKey == objectName)
      .nonEmpty

    if (exists) {
      val is = s3Client.getObject(bucketName, objectName).getObjectContent
      Source.fromInputStream(is).getLines().mkString("\n")
    } else {
      ""
    }
  }

  def lastModified() = {
    s3Client
      .listObjects(bucketName, objectName)
      .getObjectSummaries
      .filter(_.getKey == objectName)
      .map(_.getLastModified.getTime)
      .headOption
      .getOrElse(0)
  }

  def write(file: File) {
    s3Client.putObject(
      new PutObjectRequest(bucketName, objectName, file)
        .withCannedAcl(CannedAccessControlList.PublicRead))
  }

  def whenChanged(block: TestConfig => Unit) {
    var lastMod = lastModified()
    while (true) {
      Thread.sleep(whenChangedPollEveryMs)
      val newMod = lastModified()
      if (newMod != lastMod) {
        block(TestConfig.from(read()))
      }

      lastMod = newMod
    }
  }
}

object WriteTestConfigOnS3 extends App {
  if (args.size == 0) {
    println("Usage:")
    println("java ... S3TestConfigWriter [testName]")
    println("where [testName] is name of a file in the tests directory, without the .json suffix")
  } else {
    val sourceFile = new File(s"./tests/${args(0)}.json")
    if (!sourceFile.exists()) {
      println(s"File ${sourceFile.getAbsolutePath} does not exist!")
    } else {
      new TestConfigOnS3().write(sourceFile)
    }
  }
}
