package org.dissect.rdf.spark.model

import org.apache.jena.graph.{Triple => JenaTriple, Node => JenaNode, Node_ANY, Node_Literal, Node_Blank, Node_URI}
import org.apache.jena.vocabulary.RDF
import org.apache.spark.rdd.RDD
import org.apache.spark.graphx.{Graph => SparkGraph}

import scala.reflect.ClassTag

/**
 * The JenaSpark model works with Jena and Spark RDDs.
 *
 * @author Nilesh Chakraborty <nilesh@nileshc.com>
 */
trait JenaSparkRDD extends Jena with SparkRDD { type Blah = JenaSparkRDD }
trait JenaSparkGraphX extends Jena with SparkGraphX { type Blah = Jena }

trait Jena extends RDF {
  // types related to the RDF data model
  type Triple = JenaTriple
  type Node = JenaNode
  type URI = Node_URI
  type BNode = Node_Blank
  type Literal = Node_Literal
  type Lang = String

  // types for the graph traversal API
  type NodeMatch = JenaNode
  type NodeAny = Node_ANY
}

trait SparkRDD extends RDF {
  type Blah <: SparkRDD
  type Graph = RDD[Blah#Triple]
}

trait SparkGraphX extends RDF {
  type Blah <: SparkGraphX
  type Graph = SparkGraph[Blah#Node, Blah#URI]
}