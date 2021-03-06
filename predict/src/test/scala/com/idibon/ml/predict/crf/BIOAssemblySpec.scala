package com.idibon.ml.predict.crf

import com.idibon.ml.feature.language.LanguageCode
import com.idibon.ml.feature.tokenizer.{Token, TokenTransformer, Tag}
import com.idibon.ml.feature.contenttype.{ContentType, ContentTypeCode}
import com.idibon.ml.feature.StringFeature
import com.idibon.ml.predict.{PredictOptionsBuilder, PredictOptions, Span}

import com.ibm.icu.util.ULocale
import org.scalatest.{Matchers, FunSpec}

class BIOAssemblySpec extends FunSpec with Matchers with BIOAssembly {

  val tokenizer = new TokenTransformer()

  def tokenize(x: String) = tokenizer(StringFeature(x), LanguageCode(Some("eng")),
    ContentType(ContentTypeCode.PlainText)).filter(_.tag != Tag.Whitespace)

  def tag(x: String*) = (x.map(v => BIOTag(v) -> 1.0))

  val predictWithTags = (new PredictOptionsBuilder()).showTokens().showTokenTags().build()

  it("should support empty lists") {
    assemble(Seq(), Seq(), predictWithTags) shouldBe Seq()
  }

  it("should return an empty list if all tokens are outside") {
    val tokens = tokenize("hello, world")
    assemble(tokenize("hello, world"), tag("O", "O", "O"), predictWithTags) shouldBe Seq()
  }

  it("should skip over invalid INSIDE tags") {
    assemble(tokenize("hello, world"), tag("I0", "I0", "B0"), predictWithTags) shouldBe Seq(
      Span("0", 1.0f, 0, 7, 5, Seq(Token("world", Tag.Word, 7, 5)), Seq(BIOType.BEGIN)))
  }

  it("should return single-token spans") {
    assemble(tokenize("hello, world"), tag("B0", "B0", "B1"), predictWithTags) shouldBe Seq(
      Span("0", 1.0f, 0, 0, 5, Seq(Token("hello", Tag.Word, 0, 5)), Seq(BIOType.BEGIN)),
      Span("0", 1.0f, 0, 5, 1, Seq(Token(",", Tag.Punctuation, 5, 1)), Seq(BIOType.BEGIN)),
      Span("1", 1.0f, 0, 7, 5, Seq(Token("world", Tag.Word, 7, 5)), Seq(BIOType.BEGIN))
    )
  }

  it("should assemble complex spans") {
    assemble(tokenize("the quick brown fox jumped over the lazy dog"),
      tag("O", "BNP", "INP", "INP", "BV", "O", "O", "BNP", "INP"), predictWithTags) shouldBe Seq(
        Span("NP", 1.0f, 0, 4, 15,
          Seq(Token("quick", Tag.Word,4,5), Token("brown", Tag.Word,10,5), Token("fox", Tag.Word,16,3)),
          Seq(BIOType.BEGIN, BIOType.INSIDE, BIOType.INSIDE)),
        Span("V", 1.0f, 0, 20, 6, Seq(Token("jumped", Tag.Word,20,6)), Seq(BIOType.BEGIN)),
        Span("NP", 1.0f, 0, 36, 8,
          Seq(Token("lazy", Tag.Word,36,4), Token("dog", Tag.Word,41,3)),
          Seq(BIOType.BEGIN, BIOType.INSIDE)))
  }

  it("should die if the lists have different lengths") {
    intercept[IllegalArgumentException] {
      assemble(tokenize("hello, world"), tag("O"), predictWithTags)
    }
  }

  it("should not return tokens or tags if predict options says no.") {
    assemble(tokenize("the quick brown fox jumped over the lazy dog"),
      tag("O", "BNP", "INP", "INP", "BV", "O", "O", "BNP", "INP"), new PredictOptions()) shouldBe Seq(
        Span("NP", 1.0f, 0, 4, 15),
        Span("V", 1.0f, 0, 20, 6),
        Span("NP", 1.0f, 0, 36, 8))
  }

  it("should just return tags if that is specified.") {
    assemble(tokenize("the quick brown fox jumped over the lazy dog"),
      tag("O", "BNP", "INP", "INP", "BV", "O", "O", "BNP", "INP"),
        new PredictOptions(showTokenTags = true)) shouldBe Seq(
          Span("NP", 1.0f, 0, 4, 15,
            Seq(),
            Seq(BIOType.BEGIN, BIOType.INSIDE, BIOType.INSIDE)),
          Span("V", 1.0f, 0, 20, 6, Seq(), Seq(BIOType.BEGIN)),
          Span("NP", 1.0f, 0, 36, 8,
            Seq(),
            Seq(BIOType.BEGIN, BIOType.INSIDE)))
  }

  it("should just return tokens if that is specified") {
    assemble(tokenize("the quick brown fox jumped over the lazy dog"),
      tag("O", "BNP", "INP", "INP", "BV", "O", "O", "BNP", "INP"),
      new PredictOptions(showTokens = true)) shouldBe Seq(
        Span("NP", 1.0f, 0, 4, 15,
          Seq(Token("quick", Tag.Word,4,5), Token("brown", Tag.Word,10,5), Token("fox", Tag.Word,16,3)),
          Seq()),
        Span("V", 1.0f, 0, 20, 6, Seq(Token("jumped", Tag.Word,20,6)), Seq()),
        Span("NP", 1.0f, 0, 36, 8,
          Seq(Token("lazy", Tag.Word,36,4), Token("dog", Tag.Word,41,3)),
          Seq()))
  }
}
