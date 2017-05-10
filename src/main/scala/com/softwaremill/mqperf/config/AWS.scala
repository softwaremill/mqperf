package com.softwaremill.mqperf.config

import com.amazonaws.auth._
import com.amazonaws.regions.Regions

object AWS {
  val DefaultRegion: String = Regions.EU_WEST_1.getName
  val CredentialProvider: AWSCredentialsProvider = {
    new AWSCredentialsProviderChain(
      new EnvironmentVariableCredentialsProvider(),
      InstanceProfileCredentialsProvider.getInstance()
    )
  }
}
