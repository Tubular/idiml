package com.idibon.ml.common

import org.apache.spark.{SparkConf, SparkContext}
import com.typesafe.scalalogging.StrictLogging

/** Engine implementation using an in-process, embedded SparkContext */
class EmbeddedEngine extends Engine {

  /** Returns an embedded SparkContext */
  val sparkContext = EmbeddedEngine.sparkContext
}

/** Global companion object to adhere to the 1-SparkContext-per-JVM
  * restriction imposed by Spark
  */
private [this] object EmbeddedEngine {
  val sparkContext = {
    val conf = new SparkConf().setAppName("idiml")
      .set("spark.driver.host", "localhost")
      .set("spark.ui.enabled", "false")
      .setMaster("local[*]")
    new SparkContext(conf)
  }
}
