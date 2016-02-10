package com.idibon.ml.alloy

import java.io._
import com.idibon.ml.predict._
import com.idibon.ml.common._
import com.idibon.ml.feature.{FeatureInputStream, FeatureOutputStream}
import com.idibon.ml.feature.bagofwords.Word

import org.scalatest._
import org.json4s._
import org.json4s.JsonDSL._
import scala.collection.mutable.HashMap

class BaseAlloySpec extends FunSpec with Matchers {

  describe(".load") {

    it("throws an error on invalid versions") {
      val manifestJson = new ByteArrayOutputStream
      Codec.String.write(manifestJson,
        """{"name":"garbage","specVersion":"-1","idimlVersion":"0","createdAt":"2016-01-01T00:00:00Z","properties":{}}""")
      val garbage = new MemoryAlloyReader(Map("manifest.json" -> manifestJson.toByteArray))
      intercept[UnsupportedOperationException] {
        BaseAlloy.load[Classification](new EmbeddedEngine, garbage)
      }
    }

    it("loads empty version 1 models") {
      val manifestJson = new ByteArrayOutputStream
      Codec.String.write(manifestJson,
        """{"name":"garbage","specVersion":"1","idimlVersion":"0","createdAt":"2016-01-01T00:00:00Z","properties":{}}""")
      val labelFoo = Array[Byte](1, 0, 0, 0, 0, 0, 0, 0, 7, 0, 0, 0, 0, 0, 0, 0, 0xf, 3, 0x66, 0x6f, 0x6f)
      val modelsJson = new ByteArrayOutputStream
      Codec.String.write(modelsJson, "[]")
      val garbage = new MemoryAlloyReader(Map("manifest.json" -> manifestJson.toByteArray,
        "labels.dat" -> labelFoo, "models.json" -> modelsJson.toByteArray))
      val alloy = BaseAlloy.load[Classification](new EmbeddedEngine, garbage)
      alloy.name shouldBe "garbage"
      alloy.labels shouldBe List(new Label("00000000-0000-0007-0000-00000000000f", "foo"))
    }
  }

  describe(".translateUUID") {
    it("should return null on an invalid label") {
      val alloy = new BaseAlloy("garbage",
        List(new Label("00000000-0000-0000-0000-000000000000", "foo")), Map())
      alloy.translateUUID("0") shouldBe null
    }

    it("should return label objects if the UUID exists") {
      val alloy = new BaseAlloy("garbage",
        List(new Label("00000000-0000-0000-0000-000000000000", "foo")), Map())
      alloy.translateUUID("00000000-0000-0000-0000-000000000000") shouldBe alloy.labels.head
    }
  }

  describe(".validate") {
    it("saves validatable models") {
      val alloy = new BaseAlloy("garbage",
        List(new Label("00000000-0000-0000-0000-000000000000", "foo")),
        Map("model for foo" -> new LengthClassificationModel))
          with HasValidationData {
        def validationExamples = Seq(("content" -> "this is some data"))
      }

      val archive = HashMap[String, Array[Byte]]()
      alloy.save(new MemoryAlloyWriter(archive))
      archive.get("validation.dat") should not be None
      val reader = new MemoryAlloyReader(archive.toMap)
      val reload = BaseAlloy.load[Classification](new EmbeddedEngine, reader)
      HasValidationData.validate(reader, reload)
    }

    it("raises a ValidationError if validation fails") {
      val alloy = new BaseAlloy("garbage",
        List(new Label("00000000-0000-0000-0000-000000000000", "foo")),
        Map("model for foo" -> new FailsValidationModel("00000000-0000-0000-0000-000000000000", 0.75f)))
          with HasValidationData {
        def validationExamples = Seq(("content" -> "this is some data"))
      }

      val archive = HashMap[String, Array[Byte]]()
      alloy.save(new MemoryAlloyWriter(archive))
      val reader = new MemoryAlloyReader(archive.toMap)
      val reload = BaseAlloy.load[Classification](new EmbeddedEngine, reader)

      intercept[ValidationError] {
        HasValidationData.validate(reader, reload)
      }
    }
  }

  describe(".save") {
    it("creates a useful manifest") {
      val start = new java.util.Date
      Thread.sleep(1000)
      val alloy = new BaseAlloy("garbage", List(), Map())
      val archive = HashMap[String, Array[Byte]]()
      alloy.save(new MemoryAlloyWriter(archive))
      val manifest = BaseAlloy.loadManifest(new MemoryAlloyReader(archive.toMap))
      manifest.specVersion shouldBe BaseAlloy.CURRENT_SPEC.VERSION
      manifest.name shouldBe "garbage"
      manifest.createdAt should be > start
      manifest.properties("os.name") shouldBe System.getProperty("os.name")
    }

    it("saves loadable artifacts") {
      val alloy = new BaseAlloy("garbage",
        List(new Label("00000000-0000-0000-0000-000000000000", "foo"),
          new Label("00000000-0000-0000-0000-000000000001", "bar")),
        Map("model for foo" -> new DummyClassificationModel("00000000-0000-0000-0000-000000000000", 0.75f, Word("hello")),
          "model for bar" -> new DummyClassificationModel("00000000-0000-0000-0000-000000000001", 0.25f, Word("world"))))

      val archive = HashMap[String, Array[Byte]]()
      alloy.save(new MemoryAlloyWriter(archive))
      val reload = BaseAlloy.load[Classification](new EmbeddedEngine, new MemoryAlloyReader(archive.toMap))
      reload shouldBe alloy

      reload.predict("content" -> "foo", PredictOptions.DEFAULT) should contain theSameElementsAs Seq(
        Classification("00000000-0000-0000-0000-000000000000", 0.75f, 1, 0, Seq(Word("hello") -> 1.0f)),
        Classification("00000000-0000-0000-0000-000000000001", 0.25f, 1, 0, Seq(Word("world") -> 1.0f)))
    }

    it("saves training configuration data if present") {
      val alloy = new BaseAlloy("garbage", List[Label](),
          Map[String, PredictModel[Classification]]()) with HasTrainingConfig {
        def trainingConfig = ("key" -> "value") ~ ("key2" -> "value2")
      }
      val archive = HashMap[String, Array[Byte]]()
      alloy.save(new MemoryAlloyWriter(archive))

      archive.get("training.json")
        .map(bytes => Codec.String.read(new ByteArrayInputStream(bytes))) shouldBe Some("""{"key":"value","key2":"value2"}""")
    }
  }
}

class LengthClassificationModel extends PredictModel[Classification] {
  def predict(document: Document, options: PredictOptions): Seq[Classification] = {
    val content = (document.json \ "content").asInstanceOf[JString].s
    Seq(Classification("00000000-0000-0000-0000-000000000000", 1.0f / content.length, 1, 0, Seq()))
  }

  def getFeaturesUsed: org.apache.spark.mllib.linalg.Vector = ???
}

case class DummyClassificationModel(label: String, confidence: Float, feature: Word)
    extends PredictModel[Classification]
    with Archivable[DummyClassificationModel, DummyClassificationModelLoader] {

  def save(w: Alloy.Writer): Option[JObject] = {
    val r = new FeatureOutputStream(w.resource("feature"))
    r.writeFeature(feature)
    r.close
    Some(("label" -> label) ~ ("confidence" -> confidence))
  }

  def getFeaturesUsed: org.apache.spark.mllib.linalg.Vector = ???

  def predict(document: Document, options: PredictOptions): Seq[Classification] = {
    Seq(Classification(label, confidence, 1, PredictResultFlag.NO_FLAGS, Seq(feature -> 1.0f)))
  }
}

class DummyClassificationModelLoader extends ArchiveLoader[DummyClassificationModel] {
  def load(engine: Engine, reader: Option[Alloy.Reader], config: Option[JObject]) = {
    val label = (config.get \ "label").asInstanceOf[JString].s
    val confidence = (config.get \ "confidence").asInstanceOf[JDouble].num.toFloat
    val r = new FeatureInputStream(reader.get.resource("feature"))
    val feature = r.readFeature.asInstanceOf[Word]
    r.close
    DummyClassificationModel(label, confidence, feature)
  }
}

case class FailsValidationModel(label: String, confidence: Float)
    extends PredictModel[Classification]
    with Archivable[FailsValidationModel, FailsValidationModelLoader] {

  def save(w: Alloy.Writer): Option[JObject] = {
    Some(("label" -> label) ~ ("confidence" -> confidence))
  }

  def getFeaturesUsed: org.apache.spark.mllib.linalg.Vector = ???

  def predict(document: Document, options: PredictOptions): Seq[Classification] = {
    Seq(Classification(label, confidence, 1, PredictResultFlag.NO_FLAGS, Seq()))
  }
}

class FailsValidationModelLoader extends ArchiveLoader[FailsValidationModel] {

  def load(engine: Engine, reader: Option[Alloy.Reader], config: Option[JObject]) = {
    val label = (config.get \ "label").asInstanceOf[JString].s
    val confidence = (config.get \ "confidence").asInstanceOf[JDouble].num.toFloat

    FailsValidationModel(s"!$label", confidence)
  }
}