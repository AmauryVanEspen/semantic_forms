package deductions.runtime.html

import scala.xml.Elem
import java.net.URLEncoder

trait BasicWidgets {

  def makeBackLinkButton(uri: String, title: String = ""): Elem = {
    // format: OFF
    val tit = if (title == "") s" Reverse links for &lt;$uri&gt;" else title
    <button type="button" 
    		class="btn btn-info" readonly="yes" title={ tit } data-value="$uri" onclick={ s"backlinks( '$uri' )" } id={ s"BACK-$uri" }>
      <i class="glyphicon glyphicon-search"></i> 
    </button>
  }
  
  def makeDrawGraphLink( uri: String) = {
    // TODO different link when we are on localhost (transmit RDF String then) or in production (or use N3.js
    // http://localhost:9000/download?url=http%3A%2F%2Fjmvanel.free.fr%2Fjmv.rdf%23me
    val link = /*hrefDownloadPrefix + */ URLEncoder.encode( uri, "utf-8")
//      <button type="button" class="btn-primary" readonly="yes" title="Draw RDF graph"
//    	        onclick={ s"popupgraph( '$link' );" }>
//    	<form action={ s"/assets/rdfviewer/rdfviewer.html?url=$link" }>
//        <input type="submit" class="btn-primary" readonly="yes" title="Draw RDF graph"
//    	         value="Draw graph"></input> 
//    	</form >
    <a class="btn btn-default" href={ s"/assets/rdfviewer/rdfviewer.html?url=$link" } title={"Draw RDF graph for " + uri}>
			<img width="15" border="0" src="https://www.w3.org/RDF/icons/rdf_flyer.svg" alt="RDF Resource Description Framework Flyer Icon"/>
		</a>
  }
}