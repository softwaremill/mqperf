package com.softwaremill.mqperf.config

import com.amazonaws.auth.BasicAWSCredentials
import com.typesafe.config.Config

object AWSCredentialsFromEnv {
  def apply() = {
    new BasicAWSCredentials(
      sys.env("AWS_ACCESS_KEY_ID"),
      sys.env("AWS_SECRET_ACCESS_KEY"))
  }

  def apply(config: Config): Option[BasicAWSCredentials] =
    for {
      awsKeyId <- config.getStringOpt("AWS_ACCESS_KEY_ID")
      awsSecretKey <- config.getStringOpt("AWS_SECRET_ACCESS_KEY")
    } yield new BasicAWSCredentials(awsKeyId, awsSecretKey)

}
