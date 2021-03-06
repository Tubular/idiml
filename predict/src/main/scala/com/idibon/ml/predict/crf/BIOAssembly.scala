package com.idibon.ml.predict.crf

import scala.collection.mutable.ListBuffer

import com.idibon.ml.feature.tokenizer.Token
import com.idibon.ml.predict.{PredictOptions, Span}

/** Provides methods to assemble spans from B/I/O tagged tokens */
trait BIOAssembly {

  /** Generates a list of spans from the provided tokens and B/I/O tags
    *
    * Generates one span for every token tagged BEGIN, with the span length
    * including all adjacent tokens tagged INSIDE for the same label. Tokens
    * tagged INSIDE that do not follow a BEGIN are considered out-of-sequence
    * and dropped, as are tokens tagged OUTSIDE.
    *
    * The list of tokens and tags must have the same length
    *
    * @param tok list of tokens
    * @param tag list of tags for each token, with confidence
    * @param predictOptions the prediction options to know what to return
    * @return list of spans, possibly empty
    */
  def assemble(tok: Traversable[Token],
               tag: Traversable[(BIOTag, Double)],
               predictOptions: PredictOptions):
      Seq[Span] = {
    require(tok.size == tag.size, "Tokens and tags have different lengths")

    val spans = ListBuffer[Span]()

    var tagged = tok.toIterable.zip(tag.toIterable)
    while (tagged.nonEmpty) {
      tagged = tagged.head match {
        case (startTok, (BIOLabel(BIOType.BEGIN, label), confidence)) => {
          /* slurp every INSIDE token with the same label that immediately
           * follows this BEGIN token, since they belong to the same span. */
          val (inside, next) = tagged.tail.span(_ match {
            case (_, (BIOLabel(BIOType.INSIDE, label2), _)) => label == label2
            case _ => false
          })

          /* return the average of the token confidences across the span */
          val prob = (confidence + inside.map(_._2._2).sum) / (1 + inside.size)
          val (tokens, tokenTags) = getTokensAndTags(startTok, inside, predictOptions)
          spans += inside.lastOption.map({ case (endTok, _) => {
            Span(label, prob.toFloat, 0, startTok.offset, endTok.end - startTok.offset, tokens, tokenTags)
          }}).getOrElse(
            Span(label, prob.toFloat, 0, startTok.offset, startTok.length, tokens, tokenTags)
          )
          // skip over all of the I tags processed
          next
        }
        // drop anything that isn't a BEGIN tag
        case _ => tagged.tail
      }
    }
    spans.toSeq
  }

  /**
    * Returns tokens & tags based on the predict options passed.
    *
    * If the tokens predict option is passed, returns them.
    * If the token tags prediction option is passed, returns them.
    *
    * @param startTok
    * @param inside
    * @param predictOptions
    * @return tuple of possible empty sequences, representing tokens & token tags.
    */
  def getTokensAndTags(startTok: Token,
                       inside: Iterable[(Token, (BIOTag, Double))],
                       predictOptions: PredictOptions): (Seq[Token], Seq[BIOType.Value]) = {
    // grab tokens if we need to
    val tokens = if (predictOptions.includeTokens) {
      Seq(startTok) ++ inside.map({case (token, _) => token})
    } else Seq()
    // grab token tags if we need to
    val tokenTags = if (predictOptions.includeTokenTags) {
      Seq(BIOType.BEGIN) ++ inside.map({case (_, (bio, _)) => bio.bio})
    } else Seq()
    (tokens, tokenTags)
  }
}
