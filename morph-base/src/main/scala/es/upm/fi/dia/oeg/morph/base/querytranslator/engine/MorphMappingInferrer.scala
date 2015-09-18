package es.upm.fi.dia.oeg.morph.base.querytranslator.engine

import scala.collection.JavaConversions.asScalaBuffer

import org.apache.log4j.Logger

import com.hp.hpl.jena.graph.Node
import com.hp.hpl.jena.graph.Triple
import com.hp.hpl.jena.query.Query
import com.hp.hpl.jena.sparql.algebra.Algebra
import com.hp.hpl.jena.sparql.algebra.Op
import com.hp.hpl.jena.sparql.algebra.op.OpBGP
import com.hp.hpl.jena.sparql.algebra.op.OpDistinct
import com.hp.hpl.jena.sparql.algebra.op.OpFilter
import com.hp.hpl.jena.sparql.algebra.op.OpJoin
import com.hp.hpl.jena.sparql.algebra.op.OpLeftJoin
import com.hp.hpl.jena.sparql.algebra.op.OpProject
import com.hp.hpl.jena.sparql.algebra.op.OpSlice
import com.hp.hpl.jena.sparql.algebra.op.OpUnion
import com.hp.hpl.jena.sparql.expr.Expr
import com.hp.hpl.jena.sparql.expr.ExprList
import com.hp.hpl.jena.vocabulary.RDF

import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLMappingDocument
import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLTriplesMap

class MorphMappingInferrer(mappingDocument: R2RMLMappingDocument) {
    val logger = Logger.getLogger(this.getClass());
    var mapInferredTypes: Map[Node, Set[R2RMLTriplesMap]] = Map.empty

    private def addToInferredTypes(mapNodeTypes: Map[Node, Set[R2RMLTriplesMap]], node: Node, cms: Set[R2RMLTriplesMap]): Map[Node, Set[R2RMLTriplesMap]] = {
        val newVal = mapNodeTypes.get(node).map(_.intersect(cms)).getOrElse(cms)
        mapNodeTypes + (node -> newVal)
    }

    private def genericInferBGP(bgpFunc: (OpBGP) => Map[Node, Set[R2RMLTriplesMap]])(op: Op): Map[Node, Set[R2RMLTriplesMap]] = {
        op match {
            case bgp: OpBGP => bgpFunc(bgp)
            case opLeftJoin: OpLeftJoin => {
                val mapNodeTypesLeft = this.genericInferBGP(bgpFunc)(opLeftJoin.getLeft())
                val mapNodeTypesRight = this.genericInferBGP(bgpFunc)(opLeftJoin.getRight())
                MorphQueryTranslatorUtility.mapsIntersection(mapNodeTypesLeft, mapNodeTypesRight)
            }
            case opUnion: OpUnion => {
                val mapNodeTypesLeft = this.genericInferBGP(bgpFunc)(opUnion.getLeft())
                val mapNodeTypesRight = this.genericInferBGP(bgpFunc)(opUnion.getRight())
                MorphQueryTranslatorUtility.mapsIntersection(mapNodeTypesLeft, mapNodeTypesRight)
            }
            case opJoin: OpJoin => {
                val mapNodeTypesLeft = this.genericInferBGP(bgpFunc)(opJoin.getLeft())
                val mapNodeTypesRight = this.genericInferBGP(bgpFunc)(opJoin.getRight())
                MorphQueryTranslatorUtility.mapsIntersection(mapNodeTypesLeft, mapNodeTypesRight)
            }
            case opFilter: OpFilter => this.genericInferBGP(bgpFunc)(opFilter.getSubOp())
            case opDistinct: OpDistinct => this.genericInferBGP(bgpFunc)(opDistinct.getSubOp())
            case opProject: OpProject => this.genericInferBGP(bgpFunc)(opProject.getSubOp())
            case opSlice: OpSlice => this.genericInferBGP(bgpFunc)(opSlice.getSubOp())
        }
    }

    private def genericInfer(tripleFunc: (Triple) => Option[Pair[Node, Set[R2RMLTriplesMap]]])(op: Op): Map[Node, Set[R2RMLTriplesMap]] = {
        def bgpHelper(bgp: OpBGP): Map[Node, Set[R2RMLTriplesMap]] = {
            val bpTriples = bgp.getPattern().getList()
            val newMappings = bpTriples.flatMap(tripleFunc(_))
            newMappings.foldLeft(Map.empty[Node, Set[R2RMLTriplesMap]])(
                (mapNodeTypes, tpNodeCms) => tpNodeCms match {
                    case (tpNode, cms) => this.addToInferredTypes(mapNodeTypes, tpNode, cms.toSet)
                })
        }
        genericInferBGP(bgpHelper _)(op)
    }

    def infer(query: Query): Map[Node, Set[R2RMLTriplesMap]] = {
        if (this.mapInferredTypes.isEmpty) {
            val queryPattern = query.getQueryPattern();
            val opQueryPattern = Algebra.compile(queryPattern);
            this.mapInferredTypes = this.infer(opQueryPattern);
        }

        this.mapInferredTypes;
    }

    /**
     * Build a set of triples maps that can potentially create triples matching the given a
     * query pattern (triple pattern, basic graph pattern, etc.)
     */
    def infer(opQueryPattern: Op): Map[Node, Set[R2RMLTriplesMap]] = {
        if (this.mapInferredTypes.isEmpty) {

            val mapSubjectTypesByRdfType = this.inferByRDFType(opQueryPattern);
            if (logger.isTraceEnabled()) logger.trace("mapSubjectTypesByRdfType: " + mapSubjectTypesByRdfType)

            val mapSubjectTypesByPredicatesURIs = this.inferSubjectTypesByPredicatesURIs(opQueryPattern);
            if (logger.isTraceEnabled()) logger.trace("mapSubjectTypesByPredicatesURIs: " + mapSubjectTypesByPredicatesURIs)

            val mapSubjectTypesBySubjectUri = this.inferSubjectTypesBySubjectURI(opQueryPattern);
            if (logger.isTraceEnabled()) logger.trace("mapSubjectTypesBySubjectUri: " + mapSubjectTypesBySubjectUri)

            val listSubjectMapNodes = List(mapSubjectTypesByRdfType, mapSubjectTypesByPredicatesURIs, mapSubjectTypesBySubjectUri)
            val mapSubjectTypes = MorphQueryTranslatorUtility.mapsIntersection(listSubjectMapNodes);
            if (logger.isDebugEnabled()) logger.debug("Mappings for SubjectTypes: " + mapSubjectTypes)

            val mapObjectTypesByObjectsURIs = this.inferObjectTypesByObjectURI(opQueryPattern)
            if (logger.isTraceEnabled()) logger.trace("mapObjectTypesByObjectsURIs: " + mapObjectTypesByObjectsURIs)

            val mapObjectTypesWithConceptMappingsWithClassURI =
                mapObjectTypesByObjectsURIs.mapValues(_.filter(_.getMappedClassURIs.nonEmpty))
            if (logger.isTraceEnabled()) logger.trace("mapObjectTypesWithConceptMappingsWithClassURI: " + mapObjectTypesWithConceptMappingsWithClassURI)

            val mapObjectTypesByPredicatesURIs = this.inferObjectTypesByPredicateURI(opQueryPattern, mapSubjectTypes)
            if (logger.isTraceEnabled()) logger.trace("mapObjectTypesByPredicatesURIs: " + mapObjectTypesByPredicatesURIs)

            val listMapNodes = List(mapSubjectTypesByRdfType,
                mapSubjectTypesBySubjectUri,
                mapSubjectTypesByPredicatesURIs,
                mapObjectTypesWithConceptMappingsWithClassURI,
                mapObjectTypesByPredicatesURIs)
            this.mapInferredTypes = MorphQueryTranslatorUtility.mapsIntersection(listMapNodes)
            if (logger.isDebugEnabled()) logger.debug("Final mappings, mapInferredTypes: " + mapInferredTypes)
        }

        this.mapInferredTypes;
    }

    private def inferByRDFType(op: Op): Map[Node, Set[R2RMLTriplesMap]] = {
        def helper(tp: Triple): Option[Pair[Node, Set[R2RMLTriplesMap]]] = {
            if (logger.isTraceEnabled()) logger.trace("inferByRDFType.helper, tp: " + tp)
            val tpPredicate = tp.getPredicate()
            if (tpPredicate.isURI()) {
                val predicateURI = tpPredicate.getURI()
                val tpObject = tp.getObject()

                if (RDF.`type`.getURI().equalsIgnoreCase(predicateURI) && tpObject.isURI()) {
                    val subjectType = tpObject.getURI()
                    val cms = this.mappingDocument.getClassMappingsByClassURI(subjectType);

                    if (cms.nonEmpty) {
                        val tpSubject = tp.getSubject()
                        Some(tpSubject -> cms.toSet)
                    } else {
                        val errorMessage = "No rdf:type mapping for: " + subjectType
                        logger.debug(errorMessage)
                        None
                    }
                } else None
            } else None
        }
        genericInfer(helper _)(op)
    }

    private def inferSubjectsTypesByPredicateURIs(mapSubjectSTGs: Map[Node, Set[Triple]]): Map[Node, Set[R2RMLTriplesMap]] =
        mapSubjectSTGs.mapValues(stg =>
            this.mappingDocument.getClassMappingByPropertyURIs(
                stg.map(_.getPredicate())
                    .filter(_.isURI())
                    .map(_.getURI())
                    .filter(!RDF.`type`.getURI().equalsIgnoreCase(_))
            ).toSet)

    private def bgpToSTGs(triples: List[Triple]): Map[Node, Set[Triple]] =
        triples.groupBy(_.getSubject()).mapValues(_.toSet)

    private def inferSubjectTypesBySubjectURI(op: Op): Map[Node, Set[R2RMLTriplesMap]] = {
        def helper(tp: Triple): Option[Pair[Node, Set[R2RMLTriplesMap]]] = {
            if (logger.isTraceEnabled()) logger.trace("inferSubjectTypesBySubjectURI.helper, tp: " + tp)
            val tpSubject = tp.getSubject()
            if (tpSubject.isURI()) {
                val subjectURI = tpSubject.getURI()
                val subjectTypes = this.inferByURI(subjectURI)
                if (subjectTypes.nonEmpty) {
                    Some(tpSubject, subjectTypes)
                } else None
            } else None
        }
        genericInfer(helper _)(op)
    }

    private def inferObjectTypesByExprList(exprList: ExprList): Map[Node, Set[R2RMLTriplesMap]] = {
        val listOfMaps = exprList.getList().map(this.inferObjectTypesByExpr(_))
        MorphQueryTranslatorUtility.mapsIntersection(listOfMaps.toList)
    }

    private def inferObjectTypesByExpr(expr: Expr): Map[Node, Set[R2RMLTriplesMap]] = {
        val mapNodeTypesExprs: Map[Node, Set[R2RMLTriplesMap]] = {
            if (expr.isConstant()) {
                val nodeValue = expr.getConstant();
                if (nodeValue.isIRI()) {
                    val nodeURI = nodeValue.getNode();
                    val uri = nodeURI.getURI().toString();
                    val possibleTypes = this.inferByURI(uri);
                    if (possibleTypes.nonEmpty) {
                        Map(nodeURI -> possibleTypes);
                    } else Map.empty
                } else Map.empty
            } else if (expr.isFunction()) {
                val exprFunction = expr.getFunction();
                val args = exprFunction.getArgs();
                val listOfMaps = args.map(this.inferObjectTypesByExpr(_))
                MorphQueryTranslatorUtility.mapsIntersection(listOfMaps.toList);
            } else Map.empty
        }

        mapNodeTypesExprs;
    }

    private def inferObjectTypesByObjectURI(op: Op): Map[Node, Set[R2RMLTriplesMap]] = {
        def helper(tp: Triple): Option[Pair[Node, Set[R2RMLTriplesMap]]] = {
            if (logger.isTraceEnabled()) logger.trace("inferObjectTypesByObjectURI.helper, tp: " + tp)
            val tpObject = tp.getObject()
            if (tpObject.isURI()) {
                val nodeTypes = this.inferByURI(tpObject.getURI())
                if (nodeTypes.nonEmpty) {
                    Some(tpObject -> nodeTypes)
                } else None
            } else None
        }
        genericInfer(helper _)(op)
    }

    private def inferObjectTypesByPredicateURI(op: Op, mapSubjectTypes: Map[Node, Set[R2RMLTriplesMap]]): Map[Node, Set[R2RMLTriplesMap]] = {
        def helper(mapSubjectTypes: Map[Node, Set[R2RMLTriplesMap]])(tp: Triple): Option[Pair[Node, Set[R2RMLTriplesMap]]] = {
            if (logger.isTraceEnabled()) logger.trace("inferObjectTypesByPredicateURI.helper, tp: " + tp)
            val tpPredicate = tp.getPredicate()
            if (tpPredicate.isURI()) {
                val predicateURI = tpPredicate.getURI()
                if (!RDF.`type`.getURI().equalsIgnoreCase(predicateURI)) {
                    val tpSubject = tp.getSubject()
                    val subjectTypes = mapSubjectTypes.get(tpSubject)
                    val arbitraryCm = subjectTypes.flatMap(_.headOption)
                    val nodeTypes = arbitraryCm match {
                        case Some(cm) => this.mappingDocument.getPossibleRange(predicateURI, cm)
                        case None => this.mappingDocument.getPossibleRange(predicateURI)
                    }
                    if (nodeTypes.nonEmpty) {
                        val tpObject = tp.getObject()
                        Some(tpObject -> nodeTypes.toSet)
                    } else None
                } else None
            } else None
        }
        genericInfer(helper(mapSubjectTypes) _)(op)
    }

    private def inferSubjectTypesByPredicatesURIs(op: Op): Map[Node, Set[R2RMLTriplesMap]] = {
        def helper(bgp: OpBGP): Map[Node, Set[R2RMLTriplesMap]] = {
            val bpTriples = bgp.getPattern().getList()
            val mapSubjectSTGs = this.bgpToSTGs(bpTriples.toList)

            //get subject types by all the predicate URIs of the STGs
            val subjectsTypesByPredicateURIs = this.inferSubjectsTypesByPredicateURIs(mapSubjectSTGs)
            subjectsTypesByPredicateURIs.foldLeft(Map.empty[Node, Set[R2RMLTriplesMap]])(
                (mapNodeTypes, subjectAndTypes) => subjectAndTypes match {
                    case (subject, subjectTypes) => this.addToInferredTypes(mapNodeTypes, subject, subjectTypes)
                }
            )
        }
        genericInferBGP(helper _)(op)
    }

    private def getTypes(node: Node): Set[R2RMLTriplesMap] =
        this.mapInferredTypes.getOrElse(node, Set.empty).toSet

    private def inferByURI(uri: String): Set[R2RMLTriplesMap] =
        this.mappingDocument.classMappings.filter(_.isPossibleInstance(uri)).toSet

    def printInferredTypes(): String = {
        var result = new StringBuffer();
        for (key <- this.mapInferredTypes.keySet) {
            result.append(key + " : " + this.mapInferredTypes.get(key) + "\n");
        }
        result.toString();
    }
}