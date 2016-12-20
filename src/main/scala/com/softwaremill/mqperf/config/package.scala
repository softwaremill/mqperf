package com.softwaremill.mqperf

import com.typesafe.config.Config

package object config {

  implicit class RichTypesafeConfig(config: Config) {

    def getStringOpt(path: String): Option[String] = getOpt(path, _.getString(_))

    def getIntOpt(path: String): Option[Int] = getOpt(path, _.getInt(_))

    def getConfigOpt(path: String): Option[Config] = getOpt(path, _.getConfig(_))

    def getOpt[T](path: String, block: (Config, String) => T): Option[T] = {
      if (config.hasPath(path)) {
        Option(block(config, path))
      } else {
        None
      }
    }

  }

}
