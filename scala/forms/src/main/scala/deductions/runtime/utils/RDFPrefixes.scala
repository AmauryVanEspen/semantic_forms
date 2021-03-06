package deductions.runtime.utils

import org.w3.banana.RDF
import java.net.URLEncoder
import org.w3.banana.RDFOps
import org.w3.banana.RDFPrefix
import org.w3.banana.RDFSPrefix
import org.w3.banana._
  import java.net.{ URI => jURI }
  import scala.util.Try
  import scala.util.Success
  import scala.util.Failure
  import deductions.runtime.services.Configuration

/** database of Turtle prefixes;
 *  TODO possibility to add prefix-URI pairs;
 *  TODO use prefix.cc like in EulerGUI */
trait RDFPrefixes[Rdf <: RDF] {
  this: Configuration =>

  implicit val ops: RDFOps[Rdf]
  import ops._

  val commonSchemes = List("http", "https", "url")

  val restruc = Prefix[Rdf]("restruc", "http://deductions.github.io/restruc.owl.ttl#" )

  val prefixes = List(
    RDFPrefix[Rdf], RDFSPrefix[Rdf],
    XSDPrefix[Rdf],
    DCPrefix[Rdf],
    DCTPrefix[Rdf],
    FOAFPrefix[Rdf],
    LDPPrefix[Rdf],
    IANALinkPrefix[Rdf],
    WebACLPrefix[Rdf],
    CertPrefix[Rdf],
    OWLPrefix[Rdf],
    Prefix[Rdf]("dc", "http://purl.org/dc/elements/1.1/"),
    Prefix[Rdf]("schema", "http://schema.org/"),
    Prefix[Rdf]("doap", "http://usefulinc.com/ns/doap#"),
    Prefix[Rdf]("sioc", "http://rdfs.org/sioc/ns#"),
    Prefix[Rdf]("dbo", "http://dbpedia.org/ontology/"),

    // for specific form specs (FOAF, etc)
    Prefix[Rdf]("forms", "http://deductions-software.com/ontologies/forms#"),
    // form vocabulary
    Prefix[Rdf]("form", "http://deductions-software.com/ontologies/forms.owl.ttl#" ),
    Prefix[Rdf]("av", prefixAVontology ),
    restruc,
    Prefix[Rdf]("", "http://data.onisep.fr/ontologies/" )
    )
  
  val prefixesMap: Map[String, Rdf#URI] =
    prefixes.map{ pf =>
      // println(s"prefix ${pf.prefixName} : ${pf.prefixIri}")
      pf.prefixName -> URI(pf.prefixIri) }.toMap
  val prefixesMap2: Map[String, Prefix[Rdf]] =
    prefixes.map{ pf => pf.prefixName -> pf }.toMap
  val urisMap: Map[String, String] =
    prefixes.map{ pf =>
      (pf.prefixIri) ->
      pf.prefixName
      }.toMap

  /**
   * expand possibly Prefixed URI (like foaf:name),
   *  @return Some(URI("http://xmlns.com/foaf/0.1/name")),
   *  or output None
   */
  def expand(possiblyPrefixedURI: String): Option[Rdf#URI] = {
    val uri_string = possiblyPrefixedURI // URLEncoder.encode(possiblyPrefixedURI, "UTF-8")
    val tr = Try {

      val prefixOption =
        if (possiblyPrefixedURI.startsWith(":")) {
          Some("")
        } else {
          val uri = new jURI(uri_string)
          if (uri.isAbsolute() && !commonSchemes.contains(uri.getScheme)) {
            // then it's possibly a Prefixed URI like foaf:name
            Some(uri.getScheme)
          } else None
        }

      prefixOption match {
        case Some(prefix) => {
          val prefixAsURI = prefixesMap.get(prefix)
          prefixAsURI match {
            case Some(prefixIri) =>
              Some(URI(fromUri(prefixIri) + possiblyPrefixedURI.substring(prefix.length() + 1)))
            case None => None
          }
        }
        case None => None
      }
    }
    tr match {
      case Success(r) => r
      case Failure(e) => None
    }
  }

  /**
   * inverse of #expand()
   *  @return abbreviated Turtle term, eg foaf:name
   */
  def abbreviateTurtle(uri: Rdf#Node): String = {
//    println(s"abbreviateTurtle2($uri")
    val inputURI = uri.toString()
    val found = urisMap.find {
      case (uriMapped, pref) =>
//        if(pref == "xsd") println(s"abbreviateTurtle2: pref $pref , uriMapped $uriMapped")
        inputURI.startsWith(uriMapped)
    }
//    if(found.isDefined && found.get._2 == "xsd") println(s"abbreviateTurtle2: found $found")
    found match {
      case Some((iri, pref)) =>
        val id = inputURI.substring(iri.length)
//        println(s"abbreviateTurtle2: $pref:$id")
        s"$pref:$id"
      case None =>
//        println(s"abbreviateTurtle2: None")
        inputURI
    }
  }
}
