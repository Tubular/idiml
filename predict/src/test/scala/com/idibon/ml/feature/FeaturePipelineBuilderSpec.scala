package com.idibon.ml.feature

import org.apache.spark.mllib.linalg.{Vector, Vectors}
import org.json4s.native.JsonMethods.parse
import org.json4s.{JObject, JDouble}

import org.scalatest.{Matchers, FunSpec}

class FeaturePipelineBuilderSpec extends FunSpec with Matchers {

  it("should support building pipelines with multiple stages and outputs") {

    val pipeline = (FeaturePipelineBuilder.named("test")
      += (FeaturePipelineBuilder.entry("A", new TransformA, "$document"))
      += (FeaturePipelineBuilder.entry("B", new TransformB, "$document"))
      := ("A", "B"))

    val document = parse("""{"A":0.375,"B":-0.625}""").asInstanceOf[JObject]
    pipeline(document) shouldBe Seq(Vectors.dense(0.375), Vectors.dense(-0.625))
  }

  it("should support chained transforms") {
    val pipeline = (FeaturePipelineBuilder.named("test")
      += FeaturePipelineBuilder.entry("A", new TransformA, "$document")
      += FeaturePipelineBuilder.entry("C", new TransformC, "A")
      := ("C"))
    val document = parse("""{"A":-0.5}""").asInstanceOf[JObject]
    pipeline(document) shouldBe Seq(Vectors.dense(0.5))
  }
}

private[this] class TransformA extends FeatureTransformer {
  def apply(input: JObject): Vector = {
    Vectors.dense((input \ "A").asInstanceOf[JDouble].num)
  }
}

private[this] class TransformB extends FeatureTransformer {
  def apply(input: JObject): Vector = {
    Vectors.dense((input \ "B").asInstanceOf[JDouble].num)
  }
}

private[this] class TransformC extends FeatureTransformer {
  def apply(input: Vector): Vector = {
    Vectors.dense(input.toArray.map(_ + 1.0))
  }
}