/* copyright the Déductions Project
under GNU Lesser General Public License
http://www.gnu.org/licenses/lgpl.html
$Id$
 */
package deductions.runtime.abstract_syntax

import scala.collection.mutable
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import scala.language.postfixOps
import scala.language.existentials

import org.apache.log4j.Logger
import org.apache.log4j.Level

import org.w3.banana.OWLPrefix
import org.w3.banana.PointedGraph
import org.w3.banana.RDF
import org.w3.banana.RDFDSL
import org.w3.banana.RDFOps
import org.w3.banana.RDFPrefix
import org.w3.banana.RDFSPrefix
import org.w3.banana.XSDPrefix
import org.w3.banana.diesel._
import org.w3.banana.Prefix
import org.w3.banana.SparqlOps
import org.w3.banana.RDFOpsModule
import org.w3.banana.syntax._
import org.w3.banana.SparqlEngine
import org.w3.banana.LocalNameException

import deductions.runtime.utils.RDFHelpers
import deductions.runtime.utils.Timer
import deductions.runtime.services.Configuration

/** one of EditionMode, DisplayMode, CreationMode */
abstract sealed class FormMode
object EditionMode extends FormMode { override def toString() = "EditionMode" }
object DisplayMode extends FormMode { override def toString() = "DisplayMode" }
object CreationMode extends FormMode { override def toString() = "CreationMode" }

class ResourceWithLabel[Rdf <: RDF](val resource: Rdf#Node, val label: Rdf#Node) {
  def this(couple: (Rdf#Node, Rdf#Node)) =
    this(couple._1, couple._2)
  override def toString() = "" + resource + " : " + label
}
//   def apply[Rdf <: RDF](couple: (Rdf#Node, Rdf#Node) ) = new ResourceWithLabel(couple)


trait PossibleValues[Rdf <: RDF] {
  /** Correspondence between resource for a class and
   *  instances of that class with a human readable label */
  private var possibleValues = Map[Rdf#Node, Seq[ResourceWithLabel[Rdf]]]()

  def isDefined(uri: Rdf#Node): Boolean = possibleValues.contains(uri)
  def addPossibleValues(uri: Rdf#Node, values: Seq[ResourceWithLabel[Rdf]]) = {
    possibleValues = possibleValues + (uri -> values)
    resourcesWithLabel2Tuples(values)
  }
  //  def getPossibleValues( uri: Rdf#Node): Seq[ResourceWithLabel[Rdf]] = {
  //    ???
  //  }
  def getPossibleValuesAsTuple(uri: Rdf#Node): Seq[(Rdf#Node, Rdf#Node)] = {
    resourcesWithLabel2Tuples(possibleValues.getOrElse(uri, Seq()))
  }
  private def resourcesWithLabel2Tuples(values: Seq[ResourceWithLabel[Rdf]]) =
    values.map { r => (r.resource, r.label) }
}


/**
 * Factory for an abstract Form Syntax;
 * the main class here;
 * NON transactional
 */
trait FormSyntaxFactory[Rdf <: RDF, DATASET]
    extends FormModule[Rdf#Node, Rdf#URI]
    with RDFHelpers[Rdf]
    with FieldsInference[Rdf, DATASET]
    with RangeInference[Rdf, DATASET]
    with PreferredLanguageLiteral[Rdf]
    with PossibleValues[Rdf]
   	with FormConfigurationFactory[Rdf]
    with Configuration
    with Timer {

  var preferedLanguage: String = "en"
  val defaults: FormDefaults = FormModule.formDefaults
  
  import ops._

  lazy val nullURI = URI("")
  val literalInitialValue = ""
  val logger:Logger // = Logger.getRootLogger()

  override def makeURI(n: Rdf#Node): Rdf#URI = URI(foldNode(n)(
    fromUri(_), fromBNode(_), fromLiteral(_)._1))

  val rdfs = RDFSPrefix[Rdf]
  override val rdf = RDFPrefix[Rdf]
  
  
  /** create Form from an instance (subject) URI;
   *  the Form Specification is inferred from the class of instance */
  def createForm(subject: Rdf#Node,
    editable: Boolean = false,
    formGroup: Rdf#URI = nullURI)
    (implicit graph: Rdf#Graph)
  : FormModule[Rdf#Node, Rdf#URI]#FormSyntax = {

    val s1 = new Step1(subject, editable)    
//    val classs = classFromSubject(subject) // TODO several classes
//    val propsFromConfig = lookPropertieslistFormInConfiguration(classs)._1
//    val propsFromSubject = fieldsFromSubject(subject, graph)
//    val propsFromClass =
//      if (editable) {
//        fieldsFromClass(classs, graph)
//      } else Seq()
//    val propertiesList = (propsFromConfig ++ propsFromSubject ++ propsFromClass).distinct

    createFormDetailed(subject, s1.propertiesList,
      s1.classs,
      if (editable) EditionMode else DisplayMode,
      formGroup)
  }

    /** create Form from an instance (subject) URI,
     * and a Form Specification
     * ( see form_specs/foaf.form.ttl for an example ) */
  def createFormFromSpecification(
    subject: Rdf#Node,
    formSpecification: PointedGraph[Rdf],
    editable: Boolean = false,
    formGroup: Rdf#URI = nullURI)
    (implicit graph: Rdf#Graph)
  : FormModule[Rdf#Node, Rdf#URI]#FormSyntax = {

	  val s1 = new Step1(subject, editable) {
		  override val propsFromConfig = propertiesListFromFormConfiguration(
        formSpecification.pointer)(formSpecification.graph)
	  }
	  createFormDetailed(subject, s1.propertiesList,
      s1.classs,
      if (editable) EditionMode else DisplayMode,
      formGroup)
  }
  
  class Step1(subject: Rdf#Node,
    editable: Boolean = false)
    (implicit graph: Rdf#Graph) {
    val classs = classFromSubject(subject) // TODO several classes
    val propsFromConfig = lookPropertieslistFormInConfiguration(classs)._1
    val propsFromSubject = fieldsFromSubject(subject, graph)
    val propsFromClass =
      if (editable) {
        fieldsFromClass(classs, graph)
      } else Seq()
    val propertiesList = (propsFromConfig ++ propsFromSubject ++ propsFromClass).distinct
  }


  /**
   * create Form With Detailed arguments: RDF Properties;
   * For each given property (props)
   *  look at its rdfs:range ?D
   *  see if ?D is a datatype or an OWL or RDFS class;
   *  
   * used directly for creating an empty Form from a class URI,
   * and indirectly for other cases.
   */
  def createFormDetailed(subject: Rdf#Node,
    props: Iterable[Rdf#URI], classs: Rdf#URI,
    formMode: FormMode,
    formGroup: Rdf#URI = nullURI,
    formConfig: Rdf#Node = URI(""))
    (implicit graph: Rdf#Graph)
  : FormModule[Rdf#Node, Rdf#URI]#FormSyntax = {

    logger.info(s"createForm subject $subject, props $props")
    println("FormSyntaxFactory: preferedLanguage: " + preferedLanguage)
    val rdfh = this
    
    val valuesFromFormGroup = possibleValuesFromFormGroup(formGroup: Rdf#URI, graph)

    val entries = for (prop <- props) yield {
      logger.info(s"createForm subject $subject, prop $prop")
      val ranges = objectsQuery(prop, rdfs.range)
      val rangesSize = ranges.size
      System.err.println(
        if (rangesSize > 1) {
          "WARNING: ranges " + ranges + " for property " + prop + " are multiple."
        } else if (rangesSize == 0) {
          "WARNING: There is no range for property " + prop
        } else "")
      time(s"makeEntries(${prop})",
        makeEntries(subject, prop, ranges, formMode, valuesFromFormGroup))
    }
    val fields = entries.flatMap { identity }
    val fields2 = addTypeTriple(subject, classs, fields)
    //    val fields2 = fields.toSeq
    val formSyntax = FormSyntax(subject, fields2, classs)
    logger.info(s"createForm " + this)
    val res = time(s"updateFormFromConfig()",
      updateFormFromConfig(formSyntax, formConfig))
    logger.info(s"createForm 2 " + this)
    res
  }

  /**
   * update given Form,
   * looking up for Form Configuration within given RDF graph 
   * eg in :
   *  <pre>
   *  &lt;topic_interest> :fieldAppliesToForm &lt;personForm> ;
   *   :fieldAppliesToProperty foaf:topic_interest ;
   *   :widgetClass form:DBPediaLookup .
   *  <pre>
   */
  private def updateFormFromConfig(formSyntax: FormSyntax, formConfig: Rdf#Node)
  (implicit graph: Rdf#Graph)
  : FormSyntax = {
	  val formConfigFactory = this // new FormConfigurationFactory[Rdf](graph)
    for (field <- formSyntax.fields) {
      val fieldSpecs = formConfigFactory.lookFieldSpecInConfiguration(field.property)
      //    	val DEBUG2 = new Level( 5000, "DEBUG2", 7);
      if (!fieldSpecs.isEmpty)
        //        logger.log(Level.OFF, s"""updateFormFromConfig field $field -- fieldSpecs size ${fieldSpecs.size}
        //        $fieldSpecs""")
        fieldSpecs.map {
          fieldSpec =>
            val triples = find(graph, fieldSpec.subject, ANY, ANY).toSeq
            for (t <- triples) {
              // println(s"updateFormFromConfig fieldSpec $fieldSpec -- triple $t")
              field.addTriple(t.subject, t.predicate, t.objectt)
            }
            // TODO each feature should be in a different file
            for (t <- triples) {
              if (t.predicate == formPrefix("widgetClass")
                && t.objectt == formPrefix("DBPediaLookup")) {
                def replace[T](s: Seq[T], occurence: T, replacement: T): Seq[T] = {
                  s.map { i => if (i == occurence) replacement else i }
                }
                val rep = field.asResource
                rep.widgetType = DBPediaLookup
                formSyntax.fields = replace(formSyntax.fields, field, rep)
              }
            }
        }
    }
    val triples = find(graph, formConfig, ANY, ANY).toSeq
    // TODO try the Object - semantic mapping of Banana-RDF
    val uriToCardinalities = Map[Rdf#Node, Cardinality] {
      formPrefix("zeroOrMore") -> zeroOrMore;
      formPrefix("oneOrMore") -> oneOrMore;
      formPrefix("zeroOrOne") -> zeroOrOne;
      formPrefix("exactlyOne") -> exactlyOne
    }
    for (t <- triples) {
      logger.log(Level.OFF, "updateFormForClass formConfig " + t)
      if (t.predicate == formPrefix("defaultCardinality")) {
        formSyntax.defaults.defaultCardinality = uriToCardinalities.getOrElse(t.objectt, zeroOrOne)
      }
    }
    formSyntax
  }

  /**
   * add Triple: <subject> rdf:type <classs>
   *  @return augmented fields argument
   */
  def addTypeTriple(subject: Rdf#Node, classs: Rdf#URI,
    fields: Iterable[Entry])
    (implicit graph: Rdf#Graph)
  : Seq[Entry] = {
    val alreadyInDatabase = !find(graph, subject, rdf.typ, ANY).isEmpty
    if ( // defaults.displayRdfType ||
    !alreadyInDatabase) {
      val classFormEntry = new ResourceEntry(
        // TODO not I18N:
        "type", "class",
        rdf.typ, ResourceValidator(Set(owl.Class)), classs,
        alreadyInDatabase = alreadyInDatabase)
      (fields ++ Seq(classFormEntry)).toSeq
    } else fields.toSeq
  }

  /** find fields from given Instance subject */
  private def fieldsFromSubject(subject: Rdf#Node, graph: Rdf#Graph): Seq[Rdf#URI] = {
    getPredicates(graph, subject).toSeq.distinct
  }

  /**
   * try to get rdfs:label, comment, rdf:type,
   * or else display terminal Part of URI as label;
   *  taking in account multi-valued properties
   */
  private def makeEntries(subject: Rdf#Node, prop: Rdf#URI, ranges: Set[Rdf#Node],
    formMode: FormMode,
    valuesFromFormGroup: Seq[(Rdf#Node, Rdf#Node)])
	  (implicit graph: Rdf#Graph)
  : Seq[Entry] = {
    //    Timer.startTimer
    logger.info(s"makeEntries subject $subject, prop $prop")
//    implicit val gr = graph
    implicit val prlng = preferedLanguage
    val rdfh = this // makeRDFHelpersGraph(graph)
//    import rdfh.{ graph => _, ops=>_, _ }
    
    val label = getLiteralInPreferedLanguageFromSubjectAndPredicate(prop, rdfs.label, terminalPart(prop))
    val comment = getLiteralInPreferedLanguageFromSubjectAndPredicate(prop, rdfs.comment, "")
      
    val propClasses = rdfh.objectsQuery(prop, RDFPrefix[Rdf].typ)
    val objects = objectsQuery(subject, prop)
    val rangeClasses = objectsQueries(ranges, RDFPrefix[Rdf].typ)
    //    Timer.endTimer(s"makeEntries(${prop}: beginning)")

    val result = scala.collection.mutable.ArrayBuffer[Entry]()
    for (obj <- objects)
      addOneEntry(obj, formMode, valuesFromFormGroup)

    if (objects isEmpty) addOneEntry(nullURI, formMode, valuesFromFormGroup)

    //////// end of populating result of makeEntries() ////////

    def makeBN(label: String, comment: String,
      property: ObjectProperty, validator: ResourceValidator,
      value: Rdf#BNode,
      typ: Rdf#Node = nullURI) = {
      new BlankNodeEntry(label, comment, prop, ResourceValidator(ranges), value,
        type_ = typ, valueLabel = instanceLabel(value, graph, preferedLanguage)) {
        override def getId: String = fromBNode(value.asInstanceOf[Rdf#BNode])
      }
    }

    def firstType = firstNodeOrElseNullURI(ranges)

    def addOneEntry(object_ : Rdf#Node,
      formMode: FormMode,
      valuesFromFormGroup: Seq[(Rdf#Node, Rdf#Node)] //        formGroup: Rdf#URI
      ) = {

      def literalEntry = {
        // TODO match graph pattern for interval datatype ; see issue #17
        // case t if t == ("http://www.bizinnov.com/ontologies/quest.owl.ttl#interval-1-5") =>
        new LiteralEntry(label, comment, prop, DatatypeValidator(ranges),
          getLiteralNodeOrElse(object_, literalInitialValue),
          type_ = firstType, lang = getLang(object_).toString())
      }
      def resourceEntry = {
        time(s"resourceEntry object_ $object_",
          foldNode(object_)(
            object_ =>
              if (isBN(firstType)) {
                println(s""" resourceEntry makeBN "$label" """)
                makeBN(label, comment, prop, ResourceValidator(ranges), toBN(object_),
                  typ = firstType)
              } else
                new ResourceEntry(label, comment, prop, ResourceValidator(ranges), object_,
                  alreadyInDatabase = true, valueLabel = instanceLabel(object_, graph, preferedLanguage),
                  type_ = nodeSeqToURISeq(ranges).headOption.getOrElse(nullURI)),
            object_ => makeBN(label, comment, prop, ResourceValidator(ranges), object_,
              typ = firstType),
            object_ => literalEntry
          )
        )
      }

      val xsdPrefix = XSDPrefix[Rdf].prefixIri
      val rdf = RDFPrefix[Rdf]
      val rdfs = RDFSPrefix[Rdf]

      val entry = rangeClasses match {
        case _ if rangeClasses.exists { c => c.toString startsWith (xsdPrefix) } => literalEntry
        case _ if rangeClasses.contains(rdfs.Literal) => literalEntry
        case _ if propClasses.contains(owl.DatatypeProperty) => literalEntry
        case _ if ranges.contains(rdfs.Literal) => literalEntry
        case _ if propClasses.contains(owl.ObjectProperty) => resourceEntry
        case _ if rangeClasses.contains(owl.Class) => resourceEntry
        case _ if rangeClasses.contains(rdf.Property) => resourceEntry
        case _ if ranges.contains(owl.Thing) => resourceEntry
        case _ if isURI(object_) => resourceEntry
        case _ if isBN(object_) => makeBN(label, comment, prop, ResourceValidator(ranges), toBN(object_), firstType)
        case _ if object_.toString.startsWith("_:") => resourceEntry
        case _ => literalEntry
      }
      if (formMode != DisplayMode) {
        val pv = addPossibleValues(entry, ranges, valuesFromFormGroup)
        result += pv
      } else
        result += entry
    }

    logger.info("result: Entry's " + result)
    result
  }

  def isURIorBN(node: Rdf#Node) = foldNode(node)(identity, identity, x => None) != None
  override def isURI(node: Rdf#Node) = foldNode(node)(identity, x => None, x => None) != None
  def isBN(node: Rdf#Node) = foldNode(node)(x => None, identity, x => None) != None
  def toBN(node: Rdf#Node): Rdf#BNode = foldNode(node)(x => BNode(""), identity, x => BNode(""))
  def firstNodeOrElseNullURI(set: Set[Rdf#Node]): Rdf#Node = set.headOption.getOrElse(nullURI)

  /**
   * compute terminal Part of URI, eg
   *  Person from http://xmlns.com/foaf/0.1/Person
   *  Project from http://usefulinc.com/ns/doap#Project
   *  NOTE: code related for getting the ontology prefix
   */
  def terminalPart(uri: Rdf#URI): String = {
    getFragment(uri) match {
      case None => lastSegment(uri)
      case Some(frag) => frag
    }
  }

  /**
   * get first ?OBJ such that:
   *   subject predicate ?OBJ	,
   *   or returns default
   */
  private def getHeadOrElse(subject: Rdf#Node, predicate: Rdf#URI,
    default: Rdf#URI = nullURI)
    (implicit graph: Rdf#Graph)
  : Rdf#URI = {
	  val rdfh = this
    objectsQuery(subject, predicate) match {
      case ll if ll.isEmpty => default
      case ll if (isURI(ll.head)) => ll.head.asInstanceOf[Rdf#URI]
      case _ => default
    }
  }

  private def getHeadValueOrElse(subjects: Set[Rdf#Node], predicate: Rdf#URI)
  (implicit graph: Rdf#Graph)
  : Rdf#Node = {
	  val rdfh = this
    val values = objectsQueries(subjects, predicate)
    values.headOption match {
      case Some(x) => x
      case _ => nullURI
    }
  }

  def classFromSubject(subject: Rdf#Node)
  (implicit graph: Rdf#Graph)
  = {
    getHeadOrElse(subject, rdf.typ)
  }

}
