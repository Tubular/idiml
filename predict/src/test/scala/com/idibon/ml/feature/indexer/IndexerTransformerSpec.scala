package com.idibon.ml.feature.indexer

import scala.collection.mutable.HashMap

import com.idibon.ml.alloy.{MemoryAlloyReader, MemoryAlloyWriter}
import com.idibon.ml.feature.Feature
import com.idibon.ml.feature.tokenizer.{Tag, Token}
import com.idibon.ml.feature.bagofwords.Word
import com.idibon.ml.common.EmbeddedEngine
import org.apache.spark.mllib.linalg.Vectors
import org.scalatest.{Matchers, BeforeAndAfter, FunSpec}

/**
  * Tests for validating the functionality of the IndexTransformer
  *
  * @author Michelle Casbon <michelle@idibon.com>
  */

class IndexerTransformerSpec extends FunSpec with Matchers with BeforeAndAfter {

  var transform: IndexTransformer = null

  before {
    transform = new IndexTransformer(new MutableVocabulary())
  }

  describe("Indexer") {

    it("should work on an empty sequence") {
      val emptyFeatures = Seq[Feature[_]]()
      val emptyVector = Vectors.zeros(0)
      transform.apply(emptyFeatures) shouldBe emptyVector
    }

    it("should work on a sequence of Tokens") {
      val fiveTokens = Seq[Feature[Token]](
        new Token("colorless", Tag.Word, 0, 1), new Token("green", Tag.Word, 1, 1),
        new Token("ideas", Tag.Word, 0, 1), new Token("sleep", Tag.Word, 1, 1),
        new Token("furiously", Tag.Word, 1, 1))
      val expected = Vectors.sparse(5, Seq((0, 1.0), (1, 1.0), (2, 1.0), (3, 1.0), (4, 1.0)))
      transform.apply(fiveTokens) shouldBe expected
    }

    it("should work on a sequence of all the same Tokens") {
      val fiveTokens = Seq[Feature[Token]](
        new Token("colorless", Tag.Word, 0, 1), new Token("colorless", Tag.Word, 0, 1),
        new Token("colorless", Tag.Word, 0, 1), new Token("colorless", Tag.Word, 0, 1))
      val expected = Vectors.sparse(1, Array(0), Array(4.0))
      transform.apply(fiveTokens) shouldBe expected
    }

    it("should work on a sequence of Tokens with repeats") {
      val fiveTokens = Seq[Feature[Token]](
        new Token("colorless", Tag.Word, 0, 1), new Token("green", Tag.Word, 1, 1),
        new Token("ideas", Tag.Word, 0, 1), new Token("sleep", Tag.Word, 1, 1),
        new Token("sleep", Tag.Word, 1, 1), new Token("furiously", Tag.Word, 1, 1),
        new Token("green", Tag.Word, 1, 1))
      val expected = Vectors.sparse(5, Seq((0, 1.0), (1, 2.0), (2, 1.0), (3, 2.0), (4, 1.0)))
      transform.apply(fiveTokens) shouldBe expected
    }

    it("should work on a sequence of sequence of Tokens with repeats") {
      val fiveTokens = Seq[Feature[Token]](
        new Token("colorless", Tag.Word, 0, 1), new Token("green", Tag.Word, 1, 1),
        new Token("ideas", Tag.Word, 0, 1), new Token("sleep", Tag.Word, 1, 1),
        new Token("sleep", Tag.Word, 1, 1), new Token("furiously", Tag.Word, 1, 1),
        new Token("green", Tag.Word, 1, 1))
      val expected = Vectors.sparse(5, Seq((0, 2.0), (1, 4.0), (2, 2.0), (3, 4.0), (4, 2.0)))
      transform.apply(fiveTokens, fiveTokens) shouldBe expected
    }

    it("should work on a sequence of sequence of Tokens with no repeats") {
      val fiveTokens = Seq[Feature[Token]](
        new Token("colorless", Tag.Word, 0, 1), new Token("green", Tag.Word, 1, 1),
        new Token("ideas", Tag.Word, 0, 1), new Token("sleep", Tag.Word, 1, 1),
        new Token("furiously", Tag.Word, 1, 1))
      val fiveTokens2 = Seq[Feature[Token]](
        new Token("colorlessness", Tag.Word, 0, 1), new Token("greeness", Tag.Word, 1, 1),
        new Token("ideaz", Tag.Word, 0, 1), new Token("sleeping", Tag.Word, 1, 1),
        new Token("furious", Tag.Word, 1, 1))
      val expected = Vectors.sparse(10,
        Array(0,1,2,3,4,5,6,7,8,9), Array(1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0))
      transform.apply(fiveTokens, fiveTokens2) shouldBe expected
    }

    it("should work on a sequence of sequence of empty Tokens") {
      val fiveTokens = Seq[Feature[Token]]()
      val fiveTokens2 = Seq[Feature[Token]]()
      val expected = Vectors.zeros(0).toSparse
      transform.apply(fiveTokens, fiveTokens2) shouldBe expected
    }

    it("should work on a sequence of sequence of empty Tokens returning correct 0 dimension vector") {
      val fiveTokens = Seq[Feature[Token]](
        new Token("colorless", Tag.Word, 0, 1), new Token("green", Tag.Word, 1, 1),
        new Token("ideas", Tag.Word, 0, 1), new Token("sleep", Tag.Word, 1, 1),
        new Token("furiously", Tag.Word, 1, 1))
      val fiveTokens2 = Seq[Feature[Token]](
        new Token("colorlessness", Tag.Word, 0, 1), new Token("greeness", Tag.Word, 1, 1),
        new Token("ideaz", Tag.Word, 0, 1), new Token("sleeping", Tag.Word, 1, 1),
        new Token("furious", Tag.Word, 1, 1))
      transform.apply(fiveTokens, fiveTokens2)
      val no1 = Seq[Feature[Token]]()
      val no2 = Seq[Feature[Token]]()
      val expected = Vectors.zeros(0)
      transform.apply(no1, no2) shouldBe Vectors.zeros(10).toSparse
    }

    it("should save and load a transformer properly") {
      val fiveTokens = Seq[Feature[Token]](
        new Token("colorless", Tag.Word, 0, 0), new Token("green", Tag.Word, 0, 0),
        new Token("ideas", Tag.Word, 0, 0), new Token("sleep", Tag.Word, 0, 0),
        new Token("sleep", Tag.Word, 0, 0), new Token("furiously", Tag.Word, 0, 0),
        new Token("green", Tag.Word, 0, 0))
      transform.apply(fiveTokens)
      transform.freeze()

      // Save the results
      val archive = HashMap[String, Array[Byte]]()
      transform.save(new MemoryAlloyWriter(archive))

      // Load the results
      val transform2 = (new IndexTransformLoader).load(
        new EmbeddedEngine, Some(new MemoryAlloyReader(archive.toMap)), None)

      transform.vocabulary shouldBe transform2.vocabulary
    }

    it("should give the same result after applying twice") {
      val fiveTokens = Seq[Feature[Token]](
        new Token("colorless", Tag.Word, 0, 1), new Token("green", Tag.Word, 1, 1),
        new Token("ideas", Tag.Word, 0, 1), new Token("sleep", Tag.Word, 1, 1),
        new Token("furiously", Tag.Word, 1, 1))
      val expected = Vectors.sparse(5, Seq((0, 1.0), (1, 1.0), (2, 1.0), (3, 1.0), (4, 1.0)))
      transform.apply(fiveTokens) shouldBe expected
      transform.apply(fiveTokens) shouldBe expected
    }

    it("should not add new tokens after freezing") {
      val fiveTokens = Seq[Feature[Token]](
        new Token("colorless", Tag.Word, 0, 1), new Token("green", Tag.Word, 1, 1),
        new Token("ideas", Tag.Word, 0, 1), new Token("sleep", Tag.Word, 1, 1),
        new Token("furiously", Tag.Word, 1, 1))
      val expected = Vectors.sparse(5, Seq((0, 1.0), (1, 1.0), (2, 1.0), (3, 1.0), (4, 1.0)))
      transform.apply(fiveTokens) shouldBe expected
      transform.numDimensions shouldBe Some(5)
      val frozenTransform = transform.freeze()
      val threeTokens = Seq[Feature[Token]](
        new Token("colorlessness", Tag.Word, 0, 1), new Token("greenless", Tag.Word, 1, 1),
        new Token("ideas", Tag.Word, 0, 1))
      val expected2 = Vectors.sparse(5, Array(2), Array(1.0))
      frozenTransform.apply(threeTokens) shouldBe expected2
      frozenTransform.numDimensions shouldBe Some(5)
    }

    it("it should continually add new tokens when not frozen") {
      val fiveTokens = Seq[Feature[Token]](
        new Token("colorless", Tag.Word, 0, 1), new Token("green", Tag.Word, 1, 1),
        new Token("ideas", Tag.Word, 0, 1), new Token("sleep", Tag.Word, 1, 1),
        new Token("furiously", Tag.Word, 1, 1))
      val expected = Vectors.sparse(5, Seq((0, 1.0), (1, 1.0), (2, 1.0), (3, 1.0), (4, 1.0)))
      transform.apply(fiveTokens) shouldBe expected
      transform.numDimensions shouldBe Some(5)
      val threeTokens = Seq[Feature[Token]](
        new Token("colorlessness", Tag.Word, 0, 1), new Token("greenless", Tag.Word, 1, 1),
        new Token("ideas", Tag.Word, 0, 1))
      val expected2 = Vectors.sparse(7, Array(2, 5, 6), Array(1.0, 1.0, 1.0))
      transform.apply(threeTokens) shouldBe expected2
      transform.numDimensions shouldBe Some(7)
    }
  }

  describe("Prune features tests & prune integration tests") {

    def predicate1(num:Int): Boolean = {
      !List(0, 1, 2, 3, 4).contains(num)
    }
    def predicate2(num:Int): Boolean = {
      !List(10, 20, 30, 40, 4).contains(num)
    }
    def predicate3(num:Int): Boolean = {
      List(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10).contains(num)
    }
    it("should work on empty index") {
      transform.prune(predicate1)
      transform.vocabulary.assigned shouldBe 0
    }
    it("should work on non-empty index where they are used and thus should not be removed") {
      val fiveTokens = Seq[Feature[Token]](
        new Token("colorless", Tag.Word, 0, 1), new Token("green", Tag.Word, 1, 1),
        new Token("ideas", Tag.Word, 0, 1), new Token("sleep", Tag.Word, 1, 1),
        new Token("furiously", Tag.Word, 1, 1))
      transform.apply(fiveTokens)
      transform.prune(predicate1)
      transform.vocabulary.assigned shouldBe 5
    }
    it("should work on non-empty index where some indexes are not used and thus should be removed") {
      val fiveTokens = Seq[Feature[Token]](
        new Token("colorless", Tag.Word, 0, 1), new Token("green", Tag.Word, 1, 1),
        new Token("ideas", Tag.Word, 0, 1), new Token("sleep", Tag.Word, 1, 1),
        new Token("furiously", Tag.Word, 1, 1))
      transform.apply(fiveTokens)
      transform.numDimensions shouldBe Some(5)
      transform.prune(predicate2)
      transform.numDimensions shouldBe Some(5)
      transform.vocabulary.assigned shouldBe 1
      transform.vocabulary(fiveTokens(4)) shouldBe 4
    }
    it("should keep the original size once frozen and then pruned") {
      val fiveTokens = Seq[Feature[Token]](
        new Token("colorless", Tag.Word, 0, 1), new Token("green", Tag.Word, 1, 1),
        new Token("ideas", Tag.Word, 0, 1), new Token("sleep", Tag.Word, 1, 1),
        new Token("furiously", Tag.Word, 1, 1))
      transform.apply(fiveTokens)
      transform.numDimensions shouldBe Some(5)
      val frozenTransform = transform.freeze()
      frozenTransform.prune(predicate2)
      frozenTransform.numDimensions shouldBe Some(5)
      frozenTransform.vocabulary.assigned shouldBe 1
      frozenTransform.vocabulary(fiveTokens(4)) shouldBe 4
      frozenTransform.apply(fiveTokens) shouldBe Vectors.sparse(5, Array(4), Array(1.0))
    }

    it("should create, freeze, prune, save & load as expected") {
      val fiveTokens = Seq[Feature[Token]](
        new Token("colorless", Tag.Word, 0, 0), new Token("green", Tag.Word, 0, 0),
        new Token("ideas", Tag.Word, 0, 0), new Token("sleep", Tag.Word, 0, 0),
        new Token("sleep", Tag.Word, 0, 0), new Token("furiously", Tag.Word, 0, 0),
        new Token("green", Tag.Word, 0, 0))
      transform.apply(fiveTokens)
      val frozenTransform = transform.freeze()
      frozenTransform.numDimensions shouldBe Some(5)
      frozenTransform.prune(predicate2)
      // Save the results
      val archive = HashMap[String, Array[Byte]]()
      frozenTransform.save(new MemoryAlloyWriter(archive))

      // Load the results
      val transform2 = (new IndexTransformLoader).load(
        new EmbeddedEngine, Some(new MemoryAlloyReader(archive.toMap)), None)
      transform2.numDimensions shouldBe Some(5)
      frozenTransform.vocabulary shouldBe transform2.vocabulary
      transform2.apply(fiveTokens) shouldBe Vectors.sparse(5, Array(4), Array(1.0))
    }

    it("calling freeze multiple times doesn't change number of dimensions") {
      val fiveTokens = Seq[Feature[Token]](
        new Token("colorless", Tag.Word, 0, 0), new Token("green", Tag.Word, 0, 0),
        new Token("ideas", Tag.Word, 0, 0), new Token("sleep", Tag.Word, 0, 0),
        new Token("sleep", Tag.Word, 0, 0), new Token("furiously", Tag.Word, 0, 0),
        new Token("green", Tag.Word, 0, 0))
      transform.apply(fiveTokens)
      val frozenTransform = transform.freeze()
      frozenTransform.numDimensions shouldBe Some(5)
      frozenTransform.prune(predicate2)
      // Save the results
      val archive = HashMap[String, Array[Byte]]()
      frozenTransform.save(new MemoryAlloyWriter(archive))

      // Load the results
      val transform2 = (new IndexTransformLoader).load(
        new EmbeddedEngine, Some(new MemoryAlloyReader(archive.toMap)), None)
      transform2.freeze()
      transform2.numDimensions shouldBe Some(5)
      frozenTransform.vocabulary shouldBe transform2.vocabulary
      transform2.apply(fiveTokens) shouldBe Vectors.sparse(5, Array(4), Array(1.0))
    }

    it("it should return empty vector when all tokens are OOV after freezing and pruning") {
      val fiveTokens = Seq[Feature[Token]](
        new Token("colorless", Tag.Word, 0, 1), new Token("green", Tag.Word, 1, 1),
        new Token("ideas", Tag.Word, 0, 1), new Token("sleep", Tag.Word, 1, 1),
        new Token("furiously", Tag.Word, 1, 1))
      val expected = Vectors.sparse(5, Seq((0, 1.0), (1, 1.0), (2, 1.0), (3, 1.0), (4, 1.0)))
      transform.apply(fiveTokens) shouldBe expected
      val frozenTransform = transform.freeze()
      frozenTransform.prune(predicate3)
      frozenTransform.numDimensions shouldBe Some(5)
      val threeTokens = Seq[Feature[Token]](
        new Token("colorless", Tag.Word, 0, 1), new Token("green", Tag.Word, 1, 1),
        new Token("ideas", Tag.Word, 0, 1))
      val expected2 = Vectors.sparse(5, Array(), Array())
      frozenTransform.apply(threeTokens) shouldBe expected2
      frozenTransform.numDimensions shouldBe Some(5)
    }
  }

  describe("get human readable tests") {

    it("should work on empty index") {
      transform.getFeatureByIndex(99) shouldBe None
    }

    it("should work normally when not all indexes are known ") {
      val fiveTokens = Seq[Feature[Token]](
        new Token("colorless", Tag.Word, 0, 1), new Token("green", Tag.Word, 1, 1),
        new Token("ideas", Tag.Word, 0, 1), new Token("sleep", Tag.Word, 1, 1),
        new Token("furiously", Tag.Word, 1, 1))
      transform.apply(fiveTokens)
      transform.getFeatureByIndex(3) shouldBe Some(Token("sleep", Tag.Word, 1, 1))
    }

    it("should work normally and return") {
      val fiveTokens = Seq[Feature[Token]](
        new Token("colorless", Tag.Word, 0, 1), new Token("green", Tag.Word, 1, 1),
        new Token("ideas", Tag.Word, 0, 1), new Token("sleep", Tag.Word, 1, 1),
        new Token("furiously", Tag.Word, 1, 1))
      transform.apply(fiveTokens)
      fiveTokens.zipWithIndex.foreach({ case (token, index) => {
        transform.getFeatureByIndex(index) shouldBe Some(token)
      }})
    }
  }

  describe("with minimum observations = 2") {
    it ("should require at least 2 observations before adding to vocabulary") {
      val vocab = new MutableVocabulary()
      vocab.minimumObservations = 2
      val index = new IndexTransformer(vocab)
      val initial = index(Seq(Word("foo"), Word("bar"), Word("hello"), Word("world")))
      initial shouldBe Vectors.zeros(0)
      val repeated = index(Seq(Word("bar"), Word("foobar"), Word("foobar"),
        Word("world"), Word("foobar")))
      repeated.toArray shouldBe Array(1.0, 2.0, 1.0)
      index.getFeatureByIndex(0) shouldBe Some(Word("bar"))
      index.getFeatureByIndex(1) shouldBe Some(Word("foobar"))
      index.getFeatureByIndex(2) shouldBe Some(Word("world"))
    }
  }
}
