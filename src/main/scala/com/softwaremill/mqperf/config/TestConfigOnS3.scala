package com.softwaremill.mqperf.config

import com.amazonaws.services.s3.AmazonS3Client
import scala.io.Source
import scala.collection.JavaConversions._
import java.io.{ByteArrayInputStream, File}
import com.amazonaws.services.s3.model.{ObjectMetadata, CannedAccessControlList, PutObjectRequest}

class TestConfigOnS3(private val objectName: String) {
  private val s3Client = new AmazonS3Client(AWSCredentialsFromEnv())
  private val bucketName = "mqperf"
  private val whenChangedPollEveryMs = 1000L

  def this() = this("test_config.json")

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

  def write(file: File, runId: String) {
    val content = Source.fromFile(file).getLines().toList
      .map(_.replace("$runid", runId))

    val is = new ByteArrayInputStream(content.mkString("\n").getBytes)

    s3Client.putObject(
      new PutObjectRequest(bucketName, objectName, is, new ObjectMetadata())
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
    println("java ... S3TestConfigWriter [testName] [runid]")
    println("where [testName] is name of a file in the tests directory, without the .json suffix")
  } else {
    val sourceFile = new File(s"./tests/${args(0)}.json")
    if (!sourceFile.exists()) {
      println(s"File ${sourceFile.getAbsolutePath} does not exist!")
    } else {
      val runId = if (args.size >= 2) args(1) else System.currentTimeMillis().toString
      new TestConfigOnS3().write(sourceFile, runId)
    }
  }
}
