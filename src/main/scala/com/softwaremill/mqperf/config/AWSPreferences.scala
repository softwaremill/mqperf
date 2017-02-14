package com.softwaremill.mqperf.config

import com.amazonaws.AmazonWebServiceClient
import com.amazonaws.regions.{Region, Regions}

object AWSPreferences {

  val DefaultRegion: Region = Region.getRegion(Regions.EU_WEST_1)

  def configure[C <: AmazonWebServiceClient](client: C): C = {
    client.setRegion(DefaultRegion)
    client
  }

}
