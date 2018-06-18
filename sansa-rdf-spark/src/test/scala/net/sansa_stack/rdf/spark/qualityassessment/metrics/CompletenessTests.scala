package net.sansa_stack.rdf.spark.qualityassessment.metrics

import org.scalatest.FunSuite
import com.holdenkarau.spark.testing.DataFrameSuiteBase
import org.apache.jena.riot.Lang
import net.sansa_stack.rdf.spark.io._
import net.sansa_stack.rdf.spark.qualityassessment._

class CompletenessTests extends FunSuite with DataFrameSuiteBase {

  test("assessing the interlinking completeness should result in value 0") {

    val path = getClass.getResource("/rdf.nt").getPath
    val lang: Lang = Lang.NTRIPLES

    val triples = spark.rdf(lang)(path)

    val value = triples.assessInterlinkingCompleteness()
    assert(value == 0)
  }

  test("assessing the property completeness should result in value 0") {

    val path = getClass.getResource("/rdf.nt").getPath
    val lang: Lang = Lang.NTRIPLES

    val triples = spark.rdf(lang)(path)

    val value = triples.assessPropertyCompleteness()
    assert(value == 0)
  }

  test("assessing the schema completeness should result in value 0.0") {

    val path = getClass.getResource("/rdf.nt").getPath
    val lang: Lang = Lang.NTRIPLES

    val triples = spark.rdf(lang)(path)

    val ratio = triples.assessSchemaCompleteness()
    assert(ratio == 0.0)
  }
}
