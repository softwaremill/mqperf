package com.softwaremill.mqperf

import com.amazonaws.auth.BasicAWSCredentials

object AWSCredentialsFromEnv {
  def apply() = {
    new BasicAWSCredentials(
      sys.env("EC2_ACCESS_KEY_ID"),
      sys.env("EC2_SECRET_ACCESS_KEY"))
  }
}
