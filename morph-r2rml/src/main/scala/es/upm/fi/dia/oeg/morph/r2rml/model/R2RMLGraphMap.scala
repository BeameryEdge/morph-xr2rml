package es.upm.fi.dia.oeg.morph.r2rml.model

import scala.collection.JavaConversions._
import com.hp.hpl.jena.rdf.model.Resource
import es.upm.fi.dia.oeg.morph.base.Constants
import com.hp.hpl.jena.rdf.model.RDFNode
import org.apache.log4j.Logger

class R2RMLGraphMap(
    termMapType: Constants.MorphTermMapType.Value,
    termType: Option[String],
    datatype: Option[String],
    languageTag: Option[String],
    /** Reference formulation from the logical source */
    refFormulation: String)

        extends R2RMLTermMap(termMapType, termType, datatype, languageTag, None, refFormulation) {

    val inferredTermType = this.inferTermType;
    if (inferredTermType != null && !inferredTermType.equals(Constants.R2RML_IRI_URI)) {
        throw new Exception("Non IRI value is not permitted in the graph!");
    }
}

object R2RMLGraphMap {
    val logger = Logger.getLogger(this.getClass().getName());

    def apply(rdfNode: RDFNode, refFormulation: String): R2RMLGraphMap = {
        val coreProperties = R2RMLTermMap.extractCoreProperties(rdfNode);
        val termMapType = coreProperties._1;
        val termType = coreProperties._2;
        val datatype = coreProperties._3;
        val languageTag = coreProperties._4;
        val gm = new R2RMLGraphMap(termMapType, termType, datatype, languageTag, refFormulation);
        gm.parse(rdfNode)
        gm;
    }

    def extractGraphMaps(resource: Resource, refFormulation: String): Set[R2RMLGraphMap] = {
        logger.trace("Looking for graph maps")
        val tms = R2RMLTermMap.extractTermMaps(resource, Constants.MorphPOS.graph, refFormulation);
        val result = tms.map(tm => tm.asInstanceOf[R2RMLGraphMap]);
        result;
    }
}