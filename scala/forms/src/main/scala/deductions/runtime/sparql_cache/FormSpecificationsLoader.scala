package deductions.runtime.sparql_cache

import deductions.runtime.jena.RDFStoreLocalJena1Provider
import org.w3.banana.Prefix
import deductions.runtime.jena.RDFCache
import org.w3.banana.jena.Jena
import org.w3.banana.RDF
import org.w3.banana.jena.JenaModule
import deductions.runtime.services.Configuration
import deductions.runtime.services.DefaultConfiguration
import deductions.runtime.jena.ImplementationSettings

/**
 * @author jmv
 */

/** TODO put in package jena */
object FormSpecificationsLoader extends JenaModule
      with DefaultConfiguration
    with RDFCache with App
    with FormSpecificationsLoaderTrait[Jena, ImplementationSettings.DATASET]
    with RDFStoreLocalJena1Provider //    with JenaHelpers
    {
  if (args.size == 0)
    loadCommonFormSpecifications()
  else
    loadFormSpecifications(args(0))
}

trait FormSpecificationsLoaderTrait[Rdf <: RDF, DATASET]
    extends RDFCacheAlgo[Rdf, DATASET]
    //    with RDFStoreHelpers[Rdf, DATASET]
    with SitesURLForDownload
    with Configuration {

  import ops._
  import rdfStore.transactorSyntax._
  import rdfStore.graphStoreSyntax._

  val formSpecificationsGraph = URI("urn:form_specs")

  /** TRANSACTIONAL */
  def resetCommonFormSpecifications() {
    val r = dataset.rw({
      rdfStore.removeGraph(dataset, formSpecificationsGraph)
    })
  }

  /**
   * load Common Form Specifications from scala/forms/form_specs/specs.ttl
   *  on in project jmvanel/semantic_forms on github;
   *  non TRANSACTIONAL
   */
  def loadCommonFormSpecifications() {
    val all_form_specs = githubcontent +
      "/jmvanel/semantic_forms/master/scala/forms/form_specs/specs.ttl"
    loadFormSpecifications(all_form_specs)
  }

  /** non TRANSACTIONAL */
  def loadFormSpecifications(form_specs: String) {
    val from = new java.net.URL(form_specs).openStream()
    val form_specs_graph: Rdf#Graph =
      turtleReader.read(from, base = form_specs) getOrElse sys.error(
        s"couldn't read $form_specs")
//    import deductions.runtime.abstract_syntax.FormSyntaxFactory._
    val formPrefix = Prefix("form", formVocabPrefix)
    /* Retrieving triple :
     * foaf: form:ontologyHasFormSpecification <foaf.form.ttl> . */
    val triples: Iterator[Rdf#Triple] = find(form_specs_graph, ANY, formPrefix("ontologyHasFormSpecification"), ANY)
    val objects = for (triple <- triples) yield {
      triple._3 // getObject
    }
    for (obj <- objects) {
      val from = new java.net.URL(obj.toString()).openStream()
      val form_spec_graph: Rdf#Graph = turtleReader.read(from, base = obj.toString()) getOrElse sys.error(
        s"couldn't read ${obj.toString()}")
      val r = dataset.rw({
        rdfStore.appendToGraph( dataset, formSpecificationsGraph, form_spec_graph)
      })
      println("Added form_spec " + obj)
    }
  }
}