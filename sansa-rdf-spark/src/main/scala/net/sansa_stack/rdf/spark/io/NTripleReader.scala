package net.sansa_stack.rdf.spark.io

import java.io.ByteArrayInputStream
import java.net.URI

import org.apache.jena.atlas.iterator.IteratorResourceClosing
import org.apache.jena.graph.Triple
import org.apache.jena.riot.RIOT
import org.apache.jena.riot.lang.RiotParsers
import org.apache.jena.riot.system._
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.slf4j.{Logger, LoggerFactory}

import scala.reflect.ClassTag
import scala.util.Try

/**
  * An N-Triples reader. One triple per line is assumed.
  *
  * @author Lorenz Buehmann
  */
object NTripleReader {

  /**
    * Loads N-Triples data from a file or directory into an RDD.
    * The path can also contain multiple paths
    * and even wildcards, e.g.
    * `"/my/dir1,/my/paths/part-00[0-5]*,/another/dir,/a/specific/file"`
    *
    * @param session the Spark session
    * @param path    the path to the N-Triples file(s)
    * @return the RDD of triples
    */
  def load(session: SparkSession, path: URI): RDD[Triple] = {
    load(session, path.toString)
  }

  /**
    * Loads N-Triples data from a set of files or directories into an RDD.
    * The path can also contain multiple paths
    * and even wildcards, e.g.
    * `"/my/dir1,/my/paths/part-00[0-5]*,/another/dir,/a/specific/file"`
    *
    * @param session the Spark session
    * @param paths   the path to the N-Triples file(s)
    * @return the RDD of triples
    */
  def load(session: SparkSession, paths: Seq[URI]): RDD[Triple] = {
    load(session, paths.mkString(","))
  }

  /**
    * Loads N-Triples data from a file or directory into an RDD.
    * The path can also contain multiple paths
    * and even wildcards, e.g.
    * `"/my/dir1,/my/paths/part-00[0-5]*,/another/dir,/a/specific/file"`
    *
    * === Handling of errors===
    *
    * By default, it stops once a parse error occurs, i.e. a [[org.apache.jena.riot.RiotException]] will be thrown
    * generated by the underlying parser.
    *
    * The following options exist:
    *  - STOP the whole data loading process will be stopped and a `org.apache.jena.riot.RiotException` will be thrown
    *  - SKIP the line will be skipped but the data loading process will continue, an error message will be logged
    *
    *
    * ===Handling of warnings===
    *
    * If the additional checking of RDF terms is enabled, warnings during parsing can occur. For example,
    * a wrong lexical form of a literal w.r.t. to its datatype will lead to a warning.
    *
    * The following can be done with those warnings:
    *  - IGNORE the warning will just be logged to the configured logger
    *  - STOP similar to the error handling mode, the whole data loading process will be stopped and a
    *  [[org.apache.jena.riot.RiotException]] will be thrown
    *  - SKIP similar to the error handling mode, the line will be skipped but the data loading process will continue
    *
    *
    * ===Checking of RDF terms===
    * Set whether to perform checking of NTriples - defaults to no checking.
    *
    * Checking adds warnings over and above basic syntax errors.
    * This can also be used to turn warnings into exceptions if the option `stopOnWarnings` is set to STOP or SKIP.
    *
    *  - IRIs - whether IRIs confirm to all the rules of the IRI scheme
    *  - Literals: whether the lexical form conforms to the rules for the datatype.
    *  - Triples: check slots have a valid kind of RDF term (parsers usually make this a syntax error anyway).
    *
    *
    * See also the optional `errorLog` argument to control the output. The default is to log.
    *
    *
    * @param session        the Spark session
    * @param path           the path to the N-Triples file(s)
    * @param stopOnBadTerm  stop parsing on encountering a bad RDF term
    * @param stopOnWarnings stop parsing on encountering a warning
    * @param checkRDFTerms  run with checking of literals and IRIs either on or off
    * @param errorLog       the logger used for error message handling
    * @return the RDD of triples
    */
  def load(session: SparkSession, path: String,
           stopOnBadTerm: ErrorParseMode.Value = ErrorParseMode.STOP,
           stopOnWarnings: WarningParseMode.Value = WarningParseMode.IGNORE,
           checkRDFTerms: Boolean = false,
           errorLog: Logger = ErrorHandlerFactory.stdLogger)
  : RDD[Triple] = {
    // omit empty lines + comment lines
    val rdd = session.sparkContext.textFile(path)
      .filter(line => !line.trim().isEmpty & !line.trim.startsWith("#"))

    val strict = stopOnBadTerm == ErrorParseMode.STOP && stopOnWarnings == WarningParseMode.STOP

    val profileWrapper = NonSerializableObjectWrapper {
      val errorHandler =
        if (strict) {
          ErrorHandlerFactory.errorHandlerStrict(errorLog)
        } else {
          if (stopOnBadTerm == ErrorParseMode.STOP) {
            if (stopOnWarnings == ErrorParseMode.STOP || stopOnWarnings == WarningParseMode.SKIP) {
              ErrorHandlerFactory.errorHandlerStrict(errorLog)
            } else {
              ErrorHandlerFactory.errorHandlerStd(errorLog)
            }
          } else {
            ErrorHandlerFactory.errorHandlerWarn
          }
        }

      new ParserProfileStd(RiotLib.factoryRDF, errorHandler, IRIResolver.create, PrefixMapFactory.createForInput, RIOT.getContext.copy, checkRDFTerms || strict, strict)
    }

    if (stopOnBadTerm == ErrorParseMode.STOP) {
      rdd.map(line => {
        val is = new ByteArrayInputStream(line.getBytes)
        new IteratorResourceClosing[Triple](
          RiotParsers.createIteratorNTriples(is, null, profileWrapper.get), is).next()
      })
    } else rdd.flatMap(line => Try {
      val is = new ByteArrayInputStream(line.getBytes)
      new IteratorResourceClosing[Triple](
        RiotParsers.createIteratorNTriples(is, null, profileWrapper.get), is).next()
    }.toOption)
  }

  def main(args: Array[String]): Unit = {
    if (args.length == 0) println("Usage: NTripleReader <PATH_TO_FILE>")

    val path = args(0)

    val sparkSession = SparkSession.builder
      .master("local")
      .appName("N-Triples reader")
      .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      //.config("spark.kryo.registrationRequired", "true")
      //.config("spark.eventLog.enabled", "true")
      .config("spark.kryo.registrator", String.join(", ",
      "net.sansa_stack.rdf.spark.io.JenaKryoRegistrator"))
      .config("spark.default.parallelism", "4")
      .config("spark.sql.shuffle.partitions", "4")
      .getOrCreate()

    //    val rdd = NTripleReader.load(sparkSession,
    //      path,
    //      stopOnBadTerm = false,
    //      stopOnWarnings = false,
    //      checkRDFTerms = false,
    //      strict = true,
    //      LoggerFactory.getLogger("errorLog"))
    val rdd = NTripleReader.load(sparkSession,
      path,
      stopOnBadTerm = ErrorParseMode.SKIP,
      stopOnWarnings = WarningParseMode.SKIP,
      checkRDFTerms = true,
      LoggerFactory.getLogger("errorLog"))

    println(rdd.count())

    println(rdd.take(10).mkString("\n"))
  }

}

object ErrorParseMode extends Enumeration {
  val STOP, SKIP = Value
}

object WarningParseMode extends Enumeration {
  val STOP, SKIP, IGNORE = Value
}

private class NonSerializableObjectWrapper[T: ClassTag](constructor: => T) extends AnyRef with Serializable {
  @transient private lazy val instance: T = constructor

  def get: T = instance
}

private object NonSerializableObjectWrapper {
  def apply[T: ClassTag](constructor: => T): NonSerializableObjectWrapper[T] = new NonSerializableObjectWrapper[T](constructor)
}

//sealed trait ErrorParseMode {
//  case object STOP extends ErrorParseMode
//  case object SKIP extends ErrorParseMode
//}
//sealed trait WarningParseMode {
//  case object STOP extends WarningParseMode
//  case object SKIP extends WarningParseMode
//  case object IGNORE extends WarningParseMode
//}
//@enum trait ErrorParseMode {
//  object STOP
//  object SKIP
//}