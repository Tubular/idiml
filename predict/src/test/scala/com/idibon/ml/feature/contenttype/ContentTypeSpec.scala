package com.idibon.ml.feature.contenttype

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import com.idibon.ml.feature._
import org.scalatest.{Matchers, FunSpec}

class ContentTypeSpec extends FunSpec with Matchers {

  def inputStream(b: Array[Byte]) = new FeatureInputStream(new ByteArrayInputStream(b))

  describe("save / load") {

    it("should load and save correctly") {
      val bytes = new ByteArrayOutputStream
      val feat = ContentType(ContentTypeCode.HTML)
      feat.save(new FeatureOutputStream(bytes))
      val builder = new ContentTypeBuilder
      builder.build(inputStream(bytes.toByteArray)) shouldBe feat
    }

    it("should be backwards-compatible") {
      val arr = Array[Byte](0)
      val builder = new ContentTypeBuilder
      builder.build(inputStream(arr)) shouldBe ContentType(ContentTypeCode.PlainText)
      arr(0) = 1
      builder.build(inputStream(arr)) shouldBe ContentType(ContentTypeCode.HTML)
      arr(0) = 2
      builder.build(inputStream(arr)) shouldBe ContentType(ContentTypeCode.XML)
    }
  }

  describe("get") {

    it("ContentType.get should output the full feature") {
      val contentTypeHTML = new ContentType(ContentTypeCode.HTML)
      val contentTypePlainText = new ContentType(ContentTypeCode.PlainText)
      val contentTypeXML = new ContentType(ContentTypeCode.XML)

      contentTypeHTML.get shouldBe ContentType(ContentTypeCode.HTML)
      contentTypePlainText.get shouldBe ContentType(ContentTypeCode.PlainText)
      contentTypeXML.get shouldBe ContentType(ContentTypeCode.XML)
    }

    it("ContentType.getHumanReadableString should output human-readable strings") {
      val contentTypeHTML = new ContentType(ContentTypeCode.HTML)
      val contentTypePlainText = new ContentType(ContentTypeCode.PlainText)
      val contentTypeXML = new ContentType(ContentTypeCode.XML)

      contentTypeHTML.getHumanReadableString shouldBe Some("HTML")
      contentTypePlainText.getHumanReadableString shouldBe Some("PlainText")
      contentTypeXML.getHumanReadableString shouldBe Some("XML")
    }
  }
}
