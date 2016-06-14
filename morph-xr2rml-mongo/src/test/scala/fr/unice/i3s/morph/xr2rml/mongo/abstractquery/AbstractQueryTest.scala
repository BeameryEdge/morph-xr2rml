package fr.unice.i3s.morph.xr2rml.mongo.abstractquery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.hp.hpl.jena.graph.NodeFactory
import com.hp.hpl.jena.graph.Triple
import es.upm.fi.dia.oeg.morph.base.exception.MorphException
import es.upm.fi.dia.oeg.morph.base.query.AbstractQueryConditionEquals
import es.upm.fi.dia.oeg.morph.base.query.AbstractQueryProjection
import es.upm.fi.dia.oeg.morph.base.querytranslator.TPBinding
import es.upm.fi.dia.oeg.morph.r2rml.model.R2RMLTriplesMap
import es.upm.fi.dia.oeg.morph.r2rml.model.xR2RMLQuery
import es.upm.fi.dia.oeg.morph.base.query.AbstractQueryConditionNotNull
import com.hp.hpl.jena.sparql.algebra.Algebra
import com.hp.hpl.jena.query.QueryFactory
import com.hp.hpl.jena.rdf.model.ResourceFactory
import com.hp.hpl.jena.rdf.model.Resource
import com.hp.hpl.jena.rdf.model.Model

/**
 * @author Franck Michel, I3S laboratory
 *
 */
class AbstractQueryTest {

    val res = ResourceFactory.createResource("http://toto#MyTriplesMap")
    val tm = new R2RMLTriplesMap(res, null, null, null, null)
    
    val tp1 = Triple.create(NodeFactory.createVariable("x"), NodeFactory.createURI("http://tutu"), NodeFactory.createLiteral("value"))
    val tp1bis = Triple.create(NodeFactory.createVariable("x"), NodeFactory.createURI("http://tutu"), NodeFactory.createLiteral("value"))
    val tp2 = Triple.create(NodeFactory.createVariable("x"), NodeFactory.createURI("http://tutu"), NodeFactory.createLiteral("value2"))

    val tpb1 = Set(new TPBinding(tp1, tm))
    val tpb1bis = Set(new TPBinding(tp1bis, tm))
    val tpb2 = Set(new TPBinding(tp2, tm))

    val projx1 = new AbstractQueryProjection(Set("ref1"), Some("?x"))
    val projx1bis = new AbstractQueryProjection(Set("ref1"), Some("?x"))
    val projx2 = new AbstractQueryProjection(Set("ref2"), Some("?x"))
    val projy1_as_x1 = new AbstractQueryProjection(Set("ref1"), Some("?y"))
    val projy2 = new AbstractQueryProjection(Set("ref3"), Some("?y"))

    val cond1 = new AbstractQueryConditionEquals("ref1", "value1")
    val cond1bis = new AbstractQueryConditionEquals("ref1", "value1")
    val cond2 = new AbstractQueryConditionEquals("ref2", "value2")
    val cond3 = new AbstractQueryConditionEquals("ref3", "value3")

    var q1: AbstractAtomicQueryMongo = null
    var q2: AbstractAtomicQueryMongo = null
    var q: Option[AbstractAtomicQueryMongo] = None

    @Test def test_mergeAbstractAtmoicQuery_noUniq1() {
        println("------ test_mergeAbstractAtmoicQuery_noUniq1")

        val ls1 = new xR2RMLQuery("db.collection.find()", "JSONPath", None, Set.empty)
        var ls1bis = new xR2RMLQuery("db.collection.find()", "JSONPath", None, Set.empty)

        // Same From, same projection of ?x, same Where
        q1 = new AbstractAtomicQueryMongo(tpb1, ls1, Set(projx1), Set(cond1), None)
        q2 = new AbstractAtomicQueryMongo(tpb2, ls1bis, Set(projx1bis), Set(cond1bis), None)

        q = q1.mergeForInnerJoin(q2)
        assertTrue(q.isDefined)
        println(q.get)
        assertEquals(Set(new AbstractQueryConditionEquals("ref1", "value1")), q.get.where)
        assertEquals(2, q.get.tpBindings.size)
        assertTrue(q.get.tpBindings.contains(new TPBinding(tp1, tm)))
        assertTrue(q.get.tpBindings.contains(new TPBinding(tp2, tm)))

        // Same From with '' and '{}', same projection of ?x, same Where
        ls1bis = new xR2RMLQuery("db.collection.find({})", "JSONPath", None, Set.empty)
        q2 = new AbstractAtomicQueryMongo(tpb2, ls1bis, Set(projx1bis), Set(cond1bis), None)
        q = q1.mergeForInnerJoin(q2)
        assertTrue(q.isDefined)
    }

    @Test def test_mergeAbstractAtmoicQuery_noUniq2() {
        println("------ test_mergeAbstractAtmoicQuery_noUniq2")

        val ls1 = new xR2RMLQuery("db.collection.find()", "JSONPath", None, Set.empty)
        val ls2 = new xR2RMLQuery("db.collection.find({query2})", "JSONPath", None, Set.empty)
        val ls1bis = new xR2RMLQuery("db.collection.find()", "JSONPath", None, Set.empty)

        // NOT same From, same projection of ?x, same Where
        q1 = new AbstractAtomicQueryMongo(tpb1, ls1, Set(projx1), Set(cond1), None)
        q2 = new AbstractAtomicQueryMongo(tpb2, ls2, Set(projx1bis), Set(cond1bis), None)
        q = q1.mergeForInnerJoin(q2)
        assertFalse(q.isDefined)

        // Same From, NOT same projection of ?x, same Where
        q1 = new AbstractAtomicQueryMongo(tpb1, ls1, Set(projx1), Set(cond1), None)
        q2 = new AbstractAtomicQueryMongo(tpb2, ls1bis, Set(projx2), Set(cond1bis), None)
        q = q1.mergeForInnerJoin(q2)
        assertFalse(q.isDefined)

        // Same From, same projection of ?x, NOT same Where
        q1 = new AbstractAtomicQueryMongo(tpb1, ls1, Set(projx1), Set(cond1), None)
        q2 = new AbstractAtomicQueryMongo(tpb2, ls1bis, Set(projx1bis), Set(cond2), None)
        q = q1.mergeForInnerJoin(q2)
        assertFalse(q.isDefined)
    }

    @Test def test_mergeAbstractAtmoicQuery_noUniq3() {
        println("------ test_mergeAbstractAtmoicQuery_noUniq3")

        val ls1 = new xR2RMLQuery("db.collection.find({query1})", "JSONPath", None, Set.empty)
        val ls1bis = new xR2RMLQuery("db.collection.find({query1})", "JSONPath", None, Set.empty)

        // Same From, same projection of ?x, same projection of ?y as ?x, same Where
        q1 = new AbstractAtomicQueryMongo(tpb1, ls1, Set(projx1), Set(cond1), None)
        q2 = new AbstractAtomicQueryMongo(tpb2, ls1bis, Set(projx1bis, projy1_as_x1), Set(cond1bis), None)

        q = q1.mergeForInnerJoin(q2)
        assertTrue(q.isDefined)
        println(q.get)
        assertEquals("db.collection.find({query1})", q.get.from.asInstanceOf[xR2RMLQuery].query)
        assertEquals(Set(new AbstractQueryConditionEquals("ref1", "value1")), q.get.where)
        assertEquals(2, q.get.tpBindings.size)
        assertTrue(q.get.tpBindings.contains(new TPBinding(tp1, tm)))
        assertTrue(q.get.tpBindings.contains(new TPBinding(tp2, tm)))

        // Same From, same projection of ?x, ?y as other reference, same Where
        q1 = new AbstractAtomicQueryMongo(tpb1, ls1, Set(projx1), Set(cond1), None)
        q2 = new AbstractAtomicQueryMongo(tpb2, ls1bis, Set(projx1bis, projy2), Set(cond1bis), None)
        q = q1.mergeForInnerJoin(q2)
        assertFalse(q.isDefined)

        // Same From, same projection of ?x, same projection of ?y as ?x, NOT same Where
        q1 = new AbstractAtomicQueryMongo(tpb1, ls1, Set(projx1), Set(cond1), None)
        q2 = new AbstractAtomicQueryMongo(tpb2, ls1bis, Set(projx1bis, projy1_as_x1), Set(cond1bis, cond3), None)
        q = q1.mergeForInnerJoin(q2)
        assertFalse(q.isDefined)
    }

    @Test def test_mergeAbstractAtmoicQuery_Uniq1() {
        println("------ test_mergeAbstractAtmoicQuery_Uniq1")

        val ls1 = new xR2RMLQuery("db.collection.find({query1})", "JSONPath", None, Set("ref1"))
        val ls1bis = new xR2RMLQuery("db.collection.find({query1})", "JSONPath", None, Set("ref1"))

        // Same From, same projection of ?x, same Where on ?x
        q1 = new AbstractAtomicQueryMongo(tpb1, ls1, Set(projx1), Set(cond1), None)
        q2 = new AbstractAtomicQueryMongo(tpb2, ls1bis, Set(projx1bis), Set(cond1bis), None)

        q = q1.mergeForInnerJoin(q2)
        assertTrue(q.isDefined)
        println(q.get)
        assertEquals(q.get, q1)

        // Same From, same projection of ?x, ?y as other reference, same Where on ?x
        q1 = new AbstractAtomicQueryMongo(tpb1, ls1, Set(projx1), Set(cond1), None)
        q2 = new AbstractAtomicQueryMongo(tpb2, ls1bis, Set(projx1bis, projy2), Set(cond1bis, cond3), None)
        q = q1.mergeForInnerJoin(q2)
        println(q.get)

        assertTrue(q.isDefined)
        assertEquals("db.collection.find({query1})", q.get.from.asInstanceOf[xR2RMLQuery].query)
        assertEquals(2, q.get.project.size)
        assertTrue(q.get.project.contains(new AbstractQueryProjection(Set("ref1"), Some("?x"))))
        assertTrue(q.get.project.contains(new AbstractQueryProjection(Set("ref3"), Some("?y"))))
        assertEquals(2, q.get.where.size)
        assertTrue(q.get.where.contains(new AbstractQueryConditionEquals("ref1", "value1")))
        assertTrue(q.get.where.contains(new AbstractQueryConditionEquals("ref3", "value3")))
        assertEquals(2, q.get.tpBindings.size)
        assertTrue(q.get.tpBindings.contains(new TPBinding(tp1, tm)))
        assertTrue(q.get.tpBindings.contains(new TPBinding(tp2, tm)))
    }

    @Test def test_mergeAbstractAtmoicQuery_Uniq2() {
        println("------ test_mergeAbstractAtmoicQuery_Uniq2")

        val ls1 = new xR2RMLQuery("db.collection.find({query1})", "JSONPath", None, Set("ref1"))
        val ls2 = new xR2RMLQuery("db.collection.find({query1, query2})", "JSONPath", None, Set("ref1"))

        // Right From is more specific than Left From, same projection of ?x, ?y as other reference, same Where on ?x
        q1 = new AbstractAtomicQueryMongo(tpb1, ls1, Set(projx1), Set(cond1), None)
        q2 = new AbstractAtomicQueryMongo(tpb2, ls2, Set(projx1bis, projy2), Set(cond1bis, cond3), None)
        q = q1.mergeForInnerJoin(q2)
        println(q.get)

        assertTrue(q.isDefined)
        assertEquals("db.collection.find({query1,query2})", q.get.from.asInstanceOf[xR2RMLQuery].query)
        assertEquals(2, q.get.project.size)
        assertTrue(q.get.project.contains(new AbstractQueryProjection(Set("ref1"), Some("?x"))))
        assertTrue(q.get.project.contains(new AbstractQueryProjection(Set("ref3"), Some("?y"))))
        assertEquals(2, q.get.where.size)
        assertTrue(q.get.where.contains(new AbstractQueryConditionEquals("ref1", "value1")))
        assertTrue(q.get.where.contains(new AbstractQueryConditionEquals("ref3", "value3")))
        assertEquals(2, q.get.tpBindings.size)
        assertTrue(q.get.tpBindings.contains(new TPBinding(tp1, tm)))
        assertTrue(q.get.tpBindings.contains(new TPBinding(tp2, tm)))
    }

    /**
     * Implement the optimization algorithm of an inner join query on list of Ints.
     * Instead of merging queries, we replace 2 equals elements Ints with this Int.
     */
    def optimizeQueryInnerJoinAlgo(members: List[Int]): List[Int] = {

        if (members.size == 1) { // security test but abnormal case, should never happen
            println("Unexpected case: inner join with only one member: " + this.toString)
            return members
        }

        var membersV = members
        var continue = true

        while (continue) {
            for (i: Int <- 0 to (membersV.size - 2) if continue) {
                for (j: Int <- (i + 1) to (membersV.size - 1) if continue) {

                    val left = membersV(i)
                    val right = membersV(j)

                    // Inner join of 2 atomic queries
                    if (left == right) {
                        // Note: list.slice(start, end) : start included until end excluded. slice(n,n) => empty list
                        //
                        //     i     j     =>   slice(0,i),  merged(i,j),  slice(i+1,j),  slice(j+1, size)
                        // (0, 1, 2, 3, 4) =>   0         ,  merged(1,3),  2           ,  4
                        val merged = left
                        membersV = membersV.slice(0, i) ++ List(merged) ++
                            membersV.slice(i + 1, j) ++ membersV.slice(j + 1, membersV.size)
                        continue = false
                    }
                } // end for j
            } // end for i

            if (continue) {
                // There was no more change in this run, we cannot do any optimization anymore
                return membersV
            } else
                // There was a change in this run let's rerun the optimization from with the new list of members
                continue = true
        }
        throw new MorphException("We should not quit the function this way.")
    }

    @Test def test_optimizeQueryAlgo() {
        println("------ test_optimizeQueryAlgo")

        var l = List(0, 1, 2, 3, 4)
        var lopt = optimizeQueryInnerJoinAlgo(l)
        println(lopt)
        assertEquals(List(0, 1, 2, 3, 4), lopt)

        l = List(0, 1, 2, 1, 4)
        lopt = optimizeQueryInnerJoinAlgo(l)
        println(lopt)
        assertEquals(List(0, 1, 2, 4), lopt)

        l = List(0, 1, 2, 1, 4, 5, 1)
        lopt = optimizeQueryInnerJoinAlgo(l)
        println(lopt)
        assertEquals(List(0, 1, 2, 4, 5), lopt)

        l = List(0, 0)
        lopt = optimizeQueryInnerJoinAlgo(l)
        println(lopt)
        assertEquals(List(0), lopt)

        l = List(0, 1)
        lopt = optimizeQueryInnerJoinAlgo(l)
        println(lopt)
        assertEquals(List(0, 1), lopt)

        l = List(0, 1, 2, 3, 3, 3, 3, 3)
        lopt = optimizeQueryInnerJoinAlgo(l)
        println(lopt)
        assertEquals(List(0, 1, 2, 3), lopt)

        l = List(3, 3, 3, 3, 3, 4)
        lopt = optimizeQueryInnerJoinAlgo(l)
        println(lopt)
        assertEquals(List(3, 4), lopt)

        l = List(3, 3, 3, 3)
        lopt = optimizeQueryInnerJoinAlgo(l)
        println(lopt)
        assertEquals(List(3), lopt)
    }

    @Test def test_propagateConditionFromJoinedQuery1() {
        println("------ test_propagateConditionFromJoinedQuery1")

        val res = ResourceFactory.createResource("http://toto#MyTriplesMap")
        val tm = new R2RMLTriplesMap(res, null, null, null, null)

        val ls1 = new xR2RMLQuery("db.collection.find({query1})", "JSONPath", None, Set.empty)
        var ls2 = new xR2RMLQuery("db.collection.find({query1})", "JSONPath", None, Set.empty)

        var q1 = new AbstractAtomicQueryMongo(Set.empty, ls1, Set.empty, Set.empty, None)
        var q2 = new AbstractAtomicQueryMongo(Set.empty, ls2, Set.empty, Set.empty, None)

        var q = q1.propagateConditionFromJoinedQuery(q2)
        println(q)
        assertEquals(q1, q)

        // -----------------------------------------

        ls2 = new xR2RMLQuery("db.collection.find({query1, query2})", "JSONPath", None, Set.empty)
        q2 = new AbstractAtomicQueryMongo(Set.empty, ls2, Set.empty, Set.empty, None)
        q = q1.propagateConditionFromJoinedQuery(q2)
        println(q)
        assertEquals(q1, q) // no change
    }

    @Test def test_propagateConditionFromJoinedQuery2() {
        println("------ test_propagateConditionFromJoinedQuery2")

        val res = ResourceFactory.createResource("http://toto#MyTriplesMap")
        val tm = new R2RMLTriplesMap(res, null, null, null, null)

        val ls1 = new xR2RMLQuery("db.collection.find({query1})", "JSONPath", None, Set.empty)
        val ls2 = new xR2RMLQuery("db.collection.find({query1})", "JSONPath", None, Set.empty)

        // No shared variable
        val proj1 = new AbstractQueryProjection(Set("ref1"), Some("?x"))
        val proj2 = new AbstractQueryProjection(Set("ref2"), Some("?y"))

        val cond1 = new AbstractQueryConditionEquals("ref1", "value1")
        val cond2 = new AbstractQueryConditionEquals("ref2", "value2")

        var q1 = new AbstractAtomicQueryMongo(Set.empty, ls1, Set(proj1), Set(cond1), None)
        var q2 = new AbstractAtomicQueryMongo(Set.empty, ls2, Set(proj2), Set(cond2), None)

        var q = q1.propagateConditionFromJoinedQuery(q2)
        println(q)
        assertEquals(q1, q) // no change
    }

    @Test def test_propagateConditionFromJoinedQuery3() {
        println("------ test_propagateConditionFromJoinedQuery3")

        val res = ResourceFactory.createResource("http://toto#MyTriplesMap")
        val tm = new R2RMLTriplesMap(res, null, null, null, null)

        val ls1 = new xR2RMLQuery("db.collection.find({query1})", "JSONPath", None, Set.empty)
        val ls2 = new xR2RMLQuery("db.collection.find({query1})", "JSONPath", None, Set.empty)

        val proj1 = new AbstractQueryProjection(Set("ref1"), Some("?x"))
        val proj2 = new AbstractQueryProjection(Set("ref2"), Some("?x"))

        val cond1 = new AbstractQueryConditionEquals("ref1", "value1")
        val cond2 = new AbstractQueryConditionEquals("ref2", "value2")
        val cond3 = new AbstractQueryConditionNotNull("ref2")

        var q1 = new AbstractAtomicQueryMongo(Set.empty, ls1, Set(proj1), Set(cond1), None)
        var q2 = new AbstractAtomicQueryMongo(Set.empty, ls2, Set(proj2), Set(cond2), None)

        var q = q1.propagateConditionFromJoinedQuery(q2)
        println(q)
        assertEquals(ls1, q.from) // change in the From 
        assertEquals(Set(new AbstractQueryProjection(Set("ref1"), Some("?x"))), q.project) // no change in the projection
        assertTrue(q.where.contains(new AbstractQueryConditionEquals("ref1", "value1"))) // original condition
        assertTrue(q.where.contains(new AbstractQueryConditionEquals("ref1", "value2"))) // new condition

        // -----------------------------------------

        q1 = new AbstractAtomicQueryMongo(Set.empty, ls1, Set(proj1), Set(cond1), None)
        q2 = new AbstractAtomicQueryMongo(Set.empty, ls2, Set(proj2), Set(cond3), None)
        q = q1.propagateConditionFromJoinedQuery(q2)
        println(q)
        assertEquals(ls2, q.from) // change in the From 
        assertEquals(Set(new AbstractQueryProjection(Set("ref1"), Some("?x"))), q.project) // no change in the projection
        assertTrue(q.where.contains(new AbstractQueryConditionEquals("ref1", "value1"))) // original condition
        assertTrue(q.where.contains(new AbstractQueryConditionNotNull("ref1"))) // new condition
    }

    @Test def test_getProjectionsForVariable() {
        println("------ test_getProjectinosForVariable")

        val ls1 = new xR2RMLQuery("db.collection.find({query1})", "JSONPath", None, Set.empty)
        val ls2 = new xR2RMLQuery("db.collection.find({query1})", "JSONPath", None, Set.empty)

        // No shared variable
        val proj1 = new AbstractQueryProjection(Set("ref1"), Some("?x"))
        val proj2 = new AbstractQueryProjection(Set("ref2"), Some("?y"))
        val proj3 = new AbstractQueryProjection(Set("ref3"), Some("?x"))
        val proj4 = new AbstractQueryProjection(Set("ref4"), Some("?z"))
        val proj5 = new AbstractQueryProjection(Set("ref5"), Some("?q"))

        var q1 = new AbstractAtomicQueryMongo(Set.empty, null, Set(proj1, proj2), Set.empty, None)
        var q2 = new AbstractAtomicQueryMongo(Set.empty, null, Set(proj3, proj4), Set.empty, None)
        var q3 = new AbstractAtomicQueryMongo(Set.empty, null, Set(proj5), Set.empty, None)

        assertEquals(Set(proj1), q1.getProjectionsForVariable("?x"))
        assertEquals(Set(proj3), q2.getProjectionsForVariable("?x"))
        assertEquals(Set.empty, q3.getProjectionsForVariable("?x"))

        val qij = new AbstractQueryInnerJoin(List(q1, q2, q3), None)
        assertEquals(Set(proj3, proj1), qij.getProjectionsForVariable("?x"))
    }
}
