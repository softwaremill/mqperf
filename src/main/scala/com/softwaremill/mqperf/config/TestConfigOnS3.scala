package com.softwaremill.mqperf.config

import com.amazonaws.services.s3.AmazonS3Client

import scala.io.Source
import scala.collection.JavaConversions._
import java.io.{ByteArrayInputStream, File}

import com.amazonaws.services.s3.model.{CannedAccessControlList, ObjectMetadata, PutObjectRequest}
import com.typesafe.config.{Config, ConfigFactory}

class TestConfigOnS3(private val objectName: String) {

  private val s3ClientOpt: Option[AmazonS3Client] = AWSCredentialsFromEnv(TestConfigOnS3.LocalConfig).map(new AmazonS3Client(_))
  private val bucketName = "mqperf"
  private val whenChangedPollEveryMs = 1000L

  def this() = this("test_config.json")

  def read(): TestConfig = {
    val s3ConfigOpt = for {
      s3Client <- s3ClientOpt
      if s3Client.listObjects(bucketName, objectName).getObjectSummaries.exists(_.getKey == objectName)
    } yield {
      val is = s3Client.getObject(bucketName, objectName).getObjectContent
      val rawConfig = Source.fromInputStream(is).getLines().mkString("\n")
      ConfigFactory.parseString(rawConfig)
    }
    TestConfig.from(s3ConfigOpt.getOrElse(TestConfigOnS3.LocalConfig))
  }

  def lastModified(): Option[Long] =
    for {
      s3Client <- s3ClientOpt
    } yield {
      s3Client
        .listObjects(bucketName, objectName)
        .getObjectSummaries
        .find(_.getKey == objectName)
        .map(_.getLastModified.getTime)
        .getOrElse(0)
    }

  def write(file: File, runId: String): Unit = {
    val content = Source.fromFile(file).getLines().toList
      .map(_.replace("$runid", runId))

    val is = new ByteArrayInputStream(content.mkString("\n").getBytes)

    for {
      s3Client <- s3ClientOpt
    } {
      s3Client.putObject(
        new PutObjectRequest(bucketName, objectName, is, new ObjectMetadata())
          .withCannedAcl(CannedAccessControlList.PublicRead))
    }
  }

  def whenChanged(block: TestConfig => Unit) {
    lastModified() match {
      case Some(lm) =>
        var lastMod = lastModified()
        while (true) {
          Thread.sleep(whenChangedPollEveryMs)
          val newMod = lastModified()
          if (newMod != lastMod) {
            block(read())
          }

          lastMod = newMod
        }
      case None =>
        block(read())
    }
  }
}

object TestConfigOnS3 {

  val LocalConfig: Config = ConfigFactory.load()

  def create(args: Array[String]): TestConfigOnS3 = {
    if (args.length == 0) {
      new TestConfigOnS3("test_config.json")
    } else {
      new TestConfigOnS3(args(0))
    }
  }
}

object WriteTestConfigOnS3 extends App {
  if (args.isEmpty) {
    println("Usage:")
    println("java ... S3TestConfigWriter [testName] [runid]")
    println("where [testName] is name of a file in the tests directory, without the .json suffix")
  } else {
    val sourceFile = new File(s"./tests/${args(0)}.json")
    if (!sourceFile.exists()) {
      println(s"File ${sourceFile.getAbsolutePath} does not exist!")
    } else {
      val runId = if (args.length >= 2) args(1) else System.currentTimeMillis().toString
      new TestConfigOnS3().write(sourceFile, runId)
    }
  }
}
