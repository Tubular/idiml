package com.idibon.ml.train.furnace

import com.idibon.ml.feature.FeaturePipeline
import com.idibon.ml.predict.ml.{MLModel}
import com.idibon.ml.train.SparkDataGenerator
import org.apache.spark.sql.DataFrame
import org.json4s.JsonAST.JObject

/**
  * Trait that produce MLModels.
  * We stick elements into the furnance to produce items that go into an alloy.
  */
trait Furnace {

  /**
    * Function fits a model to data in the dataframe.
    *
    * @param label
    * @param data
    * @param pipeline
    * @return
    */
  def fit(label: String, data: DataFrame, pipeline: FeaturePipeline): MLModel

  /**
    * Function is used for featurizing data.
    *
    * @param rawData
    * @param dataGen
    * @param featurePipeline
    * @return
    */
  def featurizeData(rawData: () => TraversableOnce[JObject],
                    dataGen: SparkDataGenerator,
                    featurePipeline: FeaturePipeline): Option[Map[String, DataFrame]]
}


