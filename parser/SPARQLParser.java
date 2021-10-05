package parser;

import constraints.AttributeConstraint;
import constraints.NodeConstraint;
import constraints.PredicateConstraint;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Query;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.algebra.op.OpTriple;
import org.apache.jena.sparql.core.TriplePath;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.*;
import org.apache.jena.sparql.expr.nodevalue.NodeValueNode;
import org.apache.jena.sparql.pfunction.PropFuncArg;
import org.apache.jena.sparql.syntax.*;
import org.apache.jena.sparql.util.NodeUtils;

import java.util.ArrayList;

/**
 * A class representing a parser of SPARQL queries.
 *
 * model:           a model representing the dataset on which are executed the queries
 * constraints:     an object containing a list of constraints for each type of constraint
 */
public class SPARQLParser {

    private final Model model;
    private final ConstraintsList constraintsList;

    /**
     * Creates a SPARQL parser by passing two strings representing dataset and constraints files.
     * The dataset file format requested is Turtle (.ttl)
     * The constraints file format requested is JSON (.json)
     *
     * @param datasetFile a string representing the dataset file
     * @param constraintsFile a string representing the constraints file
     */
    public SPARQLParser(String datasetFile, String constraintsFile) {

        model = RDFDataMgr.loadModel(datasetFile);

        constraintsList = new ConstraintsList();
        constraintsList.readConstraintsFile(constraintsFile);

    }

    /**
     * Parses a SPARQL query implementing all the policy constraints.
     *
     * Firstly it creates a SPARQLQuery object, by passing the query string and user given as parameter.
     * Then it verifies the three types of constraints:
     * - node constraints for each variable and uris in the query pattern;
     * - predicate constraints for each triple in the query pattern;
     * - attribute constraints for each triple in the query pattern.
     * At the end it checks if the input query and the output query are different or not, that is if some constraints
     * had been applied to the query.
     *
     * @param queryString the query string to be parsed
     * @param user the user or category of users that executed the query
     */
    public void parseQuery(String queryString, String user) {

        SPARQLQuery sparqlQuery = new SPARQLQuery(queryString, user, model);

        // check node constraints

        System.out.println("--- CHECK NODE CONSTRAINTS ---");

        for (Var v: sparqlQuery.getNodeVars()) {
            checkVarNodeConstraints(v, sparqlQuery);
        }

        for (Node uri: sparqlQuery.getNodeUris()) {
            checkUriNodeConstraints(uri, sparqlQuery);
        }

        if (sparqlQuery.getOutputQuery().equals(sparqlQuery.getInputQuery()))
            System.out.println("No node constraints applied");


        // check predicate constraints

        System.out.println("\n" + "--- CHECK PREDICATE CONSTRAINTS ---");

        Query q = sparqlQuery.getOutputQuery().cloneQuery();

        for (TriplePath triple: sparqlQuery.getTriples()) {
            checkPredicateConstraints(triple, sparqlQuery);
        }

        if (sparqlQuery.getOutputQuery().equals(q))
            System.out.println("No predicate constraints applied");


        // check attribute constraints

        System.out.println("\n" + "--- CHECK ATTRIBUTE CONSTRAINTS ---");

        q = sparqlQuery.getOutputQuery().cloneQuery();

        for (TriplePath triple: sparqlQuery.getTriples()) {
            checkAttributeConstraints(triple, sparqlQuery);
        }

        if (sparqlQuery.getOutputQuery().equals(q))
            System.out.println("No attribute constraints applied");


        // show result query

        if (sparqlQuery.getOutputQuery().equals(sparqlQuery.getInputQuery()))
            System.out.println("\n" + "--- NO CONSTRAINTS APPLIED TO THE QUERY ---");

        System.out.println("\n" + "--- OUTPUT QUERY: ---");
        System.out.println(sparqlQuery.getOutputQuery());

    }


    /**
     * Checks for the given variable if there are any constraints on this type of node and in the case
     * it applies the necessary changes to the query, adding a filter or removing the triples
     *
     * @param v the variable whose constraints must be verified
     * @param sparqlQuery the SPARQLQuery object representing the query executed
     */
    public void checkVarNodeConstraints(Var v, SPARQLQuery sparqlQuery) {

        int contNotExists = 0 ;

        ArrayList<ElementFilter> filters = new ArrayList<>();

        ArrayList<Node> nodeTypes = sparqlQuery.getNodeTypesByVar(v);

        for (Node type: nodeTypes) {

            ArrayList<NodeConstraint> nodeConstraints = constraintsList.getNodeConstraints(sparqlQuery.getUser(), type);

            if (nodeConstraints.isEmpty())
                continue;

            if (nodeConstraints.get(0).hasNodes())  {

                ArrayList<Node> nodes = new ArrayList<>();

                for (NodeConstraint nc: nodeConstraints)
                    nodes.addAll(nc.getNodes());

                ExprList list = new PropFuncArg(nodes).asExprList();
                Expr e = new E_NotOneOf(new ExprVar(v), list);
                ElementFilter filter = new ElementFilter(e);

                filters.add(filter);

            }

            else {

                Triple t = new Triple(v, NodeUtils.asNode("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), nodeConstraints.get(0).getNodeType());
                Expr e = new E_NotExists(new OpTriple(t));
                ElementFilter filter = new ElementFilter(e);

                filters.add(filter);
                contNotExists++;

            }

        }

        if (contNotExists != 0 && contNotExists == nodeTypes.size()) {
            for (TriplePath triple: sparqlQuery.getTriplesByVar(v))
                sparqlQuery.removeTriple(triple);
        }

        else {
            for (ElementFilter filter: filters)
                sparqlQuery.addFilter(filter);
        }

    }


    /**
     * Checks for the given URI in the query pattern if there are any constraints on this type of node and in the case
     * it applies the necessary changes to the query, adding a filter or removing the triples
     *
     * @param uri the URI whose constraints must be verified
     * @param sparqlQuery the SPARQLQuery object representing the query executed
     */
    public void checkUriNodeConstraints(Node uri, SPARQLQuery sparqlQuery) {

        for (Node type: sparqlQuery.getNodeTypesByUri(uri)) {

            ArrayList<NodeConstraint> nodeConstraints = constraintsList.getNodeConstraints(sparqlQuery.getUser(), type);

            if (nodeConstraints.isEmpty())
                continue;

            if (nodeConstraints.get(0).hasNodes())  {

                for (NodeConstraint nc: nodeConstraints) {

                    if (nc.getNodes().contains(uri)) {
                        for (TriplePath triple: sparqlQuery.getTriplesByUri(uri))
                            sparqlQuery.removeTriple(triple);
                        return;
                    }

                }

            }

            else {

                for (TriplePath triple: sparqlQuery.getTriplesByUri(uri))
                    sparqlQuery.removeTriple(triple);
                return;

            }

        }

    }


    /**
     * Checks for the given triple if there are any constraints on the predicate and in the case
     * it applies the necessary changes to the query, adding a filter or removing the triple.
     *
     * @param triple the triple whose constraints must be verified
     * @param sparqlQuery the SPARQLQuery object representing the query executed
     */
    public void checkPredicateConstraints(TriplePath triple, SPARQLQuery sparqlQuery) {

        ArrayList<Node> subjectTypes = new ArrayList<>();

        if (triple.getSubject().isVariable())
            subjectTypes = sparqlQuery.getNodeTypesByVar(triple.getSubject());
        else if (triple.getSubject().isURI())
            subjectTypes = sparqlQuery.getNodeTypesByUri(triple.getSubject());

        ArrayList<Node> predicateTypes = new ArrayList<>();

        if (triple.getPredicate().isVariable())
            predicateTypes = sparqlQuery.getPredicateTypesByVar(triple.getPredicate());
        else if (triple.getPredicate().isURI())
            predicateTypes.add(triple.getPredicate());

        ArrayList<Node> objectTypes = new ArrayList<>();

        if (triple.getObject().isVariable())
            objectTypes = sparqlQuery.getNodeTypesByVar(triple.getObject());
        else if (triple.getObject().isURI())
            objectTypes = sparqlQuery.getNodeTypesByUri(triple.getObject());


        ArrayList<ElementFilter> filters = new ArrayList<>();
        int contNotExists = 0;
        int contSkip = 0;

        for (Node predicateType: predicateTypes) {
            for (Node subjectType: subjectTypes) {

                ArrayList<Node> subjectDomain = sparqlQuery.getDomain(predicateType, model);
                if (!subjectDomain.isEmpty() && !subjectDomain.contains(subjectType)) {
                    contSkip++;
                    continue;
                }

                for (Node objectType: objectTypes) {

                    ArrayList<Node> objectRange = sparqlQuery.getRange(predicateType, model);
                    if (!objectRange.isEmpty() && !objectRange.contains(objectType)) {
                        contSkip++;
                        continue;
                    }

                    ArrayList<PredicateConstraint> predicateConstraints = constraintsList.getPredicateConstraints(sparqlQuery.getUser(), subjectType.toString(), predicateType.toString(), objectType.toString());

                    if (predicateConstraints.isEmpty())
                        continue;

                    if (predicateConstraints.get(0).hasSubjects() || predicateConstraints.get(0).hasObjects()) {

                        ArrayList<Node> subjects = new ArrayList<>();
                        ArrayList<Node> objects = new ArrayList<>();

                        for (PredicateConstraint pc: predicateConstraints) {

                            if (pc.hasSubjects() && pc.hasObjects()) {

                                if (pc.getSubjects().size() > 1) {

                                    ExprList list = new PropFuncArg(pc.getSubjects()).asExprList();
                                    Expr e1 = new E_OneOf(new ExprVar(triple.getSubject()), list);
                                    Expr e2 = new E_Equals(new ExprVar(triple.getObject()), new NodeValueNode(pc.getObjects().get(0)));
                                    Expr e = new E_LogicalNot(new E_LogicalAnd(e1, e2));

                                    ElementFilter filter = new ElementFilter(e);
                                    filters.add(filter);

                                }

                                else {

                                    Expr e1 = new E_Equals(new ExprVar(triple.getSubject()), new NodeValueNode(pc.getSubjects().get(0)));
                                    ExprList list = new PropFuncArg(pc.getObjects()).asExprList();
                                    Expr e2 = new E_OneOf(new ExprVar(triple.getObject()), list);
                                    Expr e = new E_LogicalNot(new E_LogicalAnd(e1, e2));

                                    ElementFilter filter = new ElementFilter(e);
                                    filters.add(filter);

                                }

                            }

                            else if (pc.hasSubjects())
                                subjects.addAll(pc.getSubjects());

                            else if (pc.hasObjects())
                                objects.addAll(pc.getObjects());

                        }

                        if (triple.getSubject().isVariable()) {

                            if (!subjects.isEmpty()) {

                                ExprList list = new PropFuncArg(subjects).asExprList();
                                Expr e = new E_NotOneOf(new ExprVar(triple.getSubject()), list);
                                ElementFilter filter = new ElementFilter(e);

                                filters.add(filter);

                            }

                        }

                        else if (triple.getSubject().isURI()) {

                            if (subjects.contains(triple.getSubject())) {

                                Triple t = new Triple(triple.getSubject(), NodeUtils.asNode("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), subjectType);
                                Expr e = new E_NotExists(new OpTriple(t));
                                ElementFilter filter = new ElementFilter(e);

                                filters.add(filter);
                                contNotExists++;

                            }

                        }

                        if (triple.getObject().isVariable()) {

                            if (!objects.isEmpty()) {

                                ExprList list = new PropFuncArg(objects).asExprList();
                                Expr e = new E_NotOneOf(new ExprVar(triple.getObject()), list);
                                ElementFilter filter = new ElementFilter(e);

                                filters.add(filter);

                            }

                        }

                        else if (triple.getObject().isURI()) {

                            if (objects.contains(triple.getObject())) {

                                Triple t = new Triple(triple.getObject(), NodeUtils.asNode("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), objectType);
                                Expr e = new E_NotExists(new OpTriple(t));
                                ElementFilter filter = new ElementFilter(e);

                                filters.add(filter);
                                contNotExists++;

                            }

                        }

                    }

                    else {

                        Triple t = new Triple(triple.getSubject(), predicateType, triple.getObject());
                        Expr e = new E_NotExists(new OpTriple(t));
                        ElementFilter filter = new ElementFilter(e);

                        filters.add(filter);
                        contNotExists++;

                    }

                }
            }
        }

        if (contNotExists != 0 && contNotExists == predicateTypes.size() * subjectTypes.size() * objectTypes.size() - contSkip)
            sparqlQuery.removeTriple(triple);

        else {
            for (ElementFilter filter: filters)
                sparqlQuery.addFilter(filter);
        }

    }


    /**
     * Checks for the given triple if there are any constraints on the attribute and in the case
     * it applies the necessary changes to the query, adding a filter or removing the triple.
     *
     * @param triple the triple whose constraints must be verified
     * @param sparqlQuery the SPARQLQuery object representing the query executed
     */
    public void checkAttributeConstraints(TriplePath triple, SPARQLQuery sparqlQuery) {

        ArrayList<Node> subjectTypes = new ArrayList<>();

        if (triple.getSubject().isVariable())
            subjectTypes = sparqlQuery.getNodeTypesByVar(triple.getSubject());
        else if (triple.getSubject().isURI())
            subjectTypes = sparqlQuery.getNodeTypesByUri(triple.getSubject());

        ArrayList<Node> predicateTypes = new ArrayList<>();

        if (triple.getPredicate().isVariable())
            predicateTypes = sparqlQuery.getPredicateTypesByVar(triple.getPredicate());
        else if (triple.getPredicate().isURI())
            predicateTypes.add(triple.getPredicate());


        ArrayList<ElementFilter> filters = new ArrayList<>();
        int contNotExists = 0;
        int contSkip = 0;

        for (Node predicateType: predicateTypes) {

            for (Node subjectType: subjectTypes) {

                ArrayList<Node> subjectDomain = sparqlQuery.getDomain(predicateType, model);
                if (!subjectDomain.isEmpty() && !subjectDomain.contains(subjectType)) {
                    contSkip++;
                    continue;
                }

                ArrayList<AttributeConstraint> attributeConstraints = constraintsList.getAttributeConstraints(sparqlQuery.getUser(), subjectType.toString(), predicateType.toString());

                if (attributeConstraints.isEmpty())
                    continue;

                boolean subjectVar = triple.getSubject().isVariable();
                boolean subjectUri = triple.getSubject().isURI();
                boolean predicateVar = triple.getPredicate().isVariable();
                boolean objectVar = triple.getObject().isVariable();
                boolean objectLiteral = triple.getObject().isLiteral();

                for (AttributeConstraint ac: attributeConstraints) {
                    
                    switch (ac.getSymbol()) {
                        
                        case "X":

                            if (ac.hasSubjects()) {

                                ArrayList<Node> subjects = ac.getSubjects();

                                if (subjectVar) {

                                    ExprList list = new PropFuncArg(subjects).asExprList();
                                    Expr e = new E_NotOneOf(new ExprVar(triple.getSubject()), list);
                                    ElementFilter filter = new ElementFilter(e);

                                    filters.add(filter);

                                }

                                else if (subjectUri && subjects.contains(triple.getSubject())) {

                                    if (predicateVar) {

                                        Triple t = new Triple(triple.getSubject(), predicateType, triple.getObject());
                                        Expr e = new E_NotExists(new OpTriple(t));
                                        ElementFilter filter = new ElementFilter(e);

                                        filters.add(filter);
                                        contNotExists++;

                                    }

                                    else {

                                        sparqlQuery.removeTriple(triple);
                                        return;

                                    }



                                }

                            }

                            else {

                                if (predicateVar) {

                                    Triple t = new Triple(triple.getSubject(), predicateType, triple.getObject());
                                    Expr e = new E_NotExists(new OpTriple(t));
                                    ElementFilter filter = new ElementFilter(e);

                                    filters.add(filter);
                                    contNotExists++;

                                }

                                else {

                                    sparqlQuery.removeTriple(triple);
                                    return;

                                }



                            }

                            break;

                        case "=":

                            if (objectVar) {

                                Expr e = new E_Equals(new ExprVar(triple.getObject()), ac.getExprValues().get(0));
                                ElementFilter filter = new ElementFilter(e);

                                filters.add(filter);

                            }

                            else if (objectLiteral && !ac.getValues().get(0).equals(triple.getObject().getLiteralValue().toString())) {

                                if (predicateVar) {

                                    Triple t = new Triple(triple.getSubject(), predicateType, triple.getObject());
                                    Expr e = new E_NotExists(new OpTriple(t));
                                    ElementFilter filter = new ElementFilter(e);

                                    filters.add(filter);
                                    contNotExists++;

                                }

                                else {

                                    sparqlQuery.removeTriple(triple);
                                    return;

                                }

                            }

                            break;
                            
                        case "!=":

                            if (objectVar) {

                                Expr e = new E_NotEquals(new ExprVar(triple.getObject()), ac.getExprValues().get(0));
                                ElementFilter filter = new ElementFilter(e);

                                filters.add(filter);

                            }

                            else if (objectLiteral && ac.getValues().get(0).equals(triple.getObject().getLiteralValue().toString())) {

                                if (predicateVar) {

                                    Triple t = new Triple(triple.getSubject(), predicateType, triple.getObject());
                                    Expr e = new E_NotExists(new OpTriple(t));
                                    ElementFilter filter = new ElementFilter(e);

                                    filters.add(filter);
                                    contNotExists++;

                                }

                                else {

                                    sparqlQuery.removeTriple(triple);
                                    return;

                                }

                            }

                            break;

                        case ">":

                            if (objectVar) {

                                Expr e = new E_GreaterThan(new ExprVar(triple.getObject()), ac.getExprValues().get(0));
                                ElementFilter filter = new ElementFilter(e);

                                filters.add(filter);

                            }

                            else if (objectLiteral && ac.compare(triple.getObject().getLiteralValue().toString()) >= 0) {

                                if (predicateVar) {

                                    Triple t = new Triple(triple.getSubject(), predicateType, triple.getObject());
                                    Expr e = new E_NotExists(new OpTriple(t));
                                    ElementFilter filter = new ElementFilter(e);

                                    filters.add(filter);
                                    contNotExists++;

                                }

                                else {

                                    sparqlQuery.removeTriple(triple);
                                    return;

                                }
                            }

                            break;

                        case ">=":

                            if (objectVar) {

                                Expr e = new E_GreaterThanOrEqual(new ExprVar(triple.getObject()), ac.getExprValues().get(0));
                                ElementFilter filter = new ElementFilter(e);

                                filters.add(filter);

                            }

                            else if (objectLiteral && ac.compare(triple.getObject().getLiteralValue().toString()) > 0) {

                                if (predicateVar) {

                                    Triple t = new Triple(triple.getSubject(), predicateType, triple.getObject());
                                    Expr e = new E_NotExists(new OpTriple(t));
                                    ElementFilter filter = new ElementFilter(e);

                                    filters.add(filter);
                                    contNotExists++;

                                }

                                else {

                                    sparqlQuery.removeTriple(triple);
                                    return;

                                }

                            }

                            break;

                        case "<":

                            if (objectVar) {

                                Expr e = new E_LessThan(new ExprVar(triple.getObject()), ac.getExprValues().get(0));
                                ElementFilter filter = new ElementFilter(e);

                                filters.add(filter);

                            }

                            else if (objectLiteral && ac.compare(triple.getObject().getLiteralValue().toString()) <= 0) {

                                if (predicateVar) {

                                    Triple t = new Triple(triple.getSubject(), predicateType, triple.getObject());
                                    Expr e = new E_NotExists(new OpTriple(t));
                                    ElementFilter filter = new ElementFilter(e);

                                    filters.add(filter);
                                    contNotExists++;

                                }

                                else {

                                    sparqlQuery.removeTriple(triple);
                                    return;

                                }

                            }

                            break;

                        case "<=":

                            if (objectVar) {

                                Expr e = new E_LessThanOrEqual(new ExprVar(triple.getObject()), ac.getExprValues().get(0));
                                ElementFilter filter = new ElementFilter(e);

                                filters.add(filter);

                            }

                            else if (objectLiteral && ac.compare(triple.getObject().getLiteralValue().toString()) < 0) {

                                if (predicateVar) {

                                    Triple t = new Triple(triple.getSubject(), predicateType, triple.getObject());
                                    Expr e = new E_NotExists(new OpTriple(t));
                                    ElementFilter filter = new ElementFilter(e);

                                    filters.add(filter);
                                    contNotExists++;

                                }

                                else {

                                    sparqlQuery.removeTriple(triple);
                                    return;

                                }

                            }

                            break;

                        case "between":

                            if (objectVar) {

                                Expr e = new E_GreaterThanOrEqual(new ExprVar(triple.getObject()), ac.getExprValues().get(0));
                                ElementFilter filter = new ElementFilter(e);

                                filters.add(filter);

                                e = new E_LessThanOrEqual(new ExprVar(triple.getObject()), ac.getExprValues().get(1));
                                filter = new ElementFilter(e);

                                filters.add(filter);

                            }

                            else if (objectLiteral && !ac.between(triple.getObject().getLiteralValue().toString())) {

                                if (predicateVar) {

                                    Triple t = new Triple(triple.getSubject(), predicateType, triple.getObject());
                                    Expr e = new E_NotExists(new OpTriple(t));
                                    ElementFilter filter = new ElementFilter(e);

                                    filters.add(filter);
                                    contNotExists++;

                                }

                                else {

                                    sparqlQuery.removeTriple(triple);
                                    return;

                                }

                            }

                            break;

                        case "in":

                            if (objectVar) {

                                Expr e = new E_OneOf(new ExprVar(triple.getObject()), new ExprList(ac.getExprValues()));
                                ElementFilter filter = new ElementFilter(e);

                                filters.add(filter);

                            }

                            else if (objectLiteral && !ac.contains(triple.getObject().getLiteralValue().toString())) {

                                if (predicateVar) {

                                    Triple t = new Triple(triple.getSubject(), predicateType, triple.getObject());
                                    Expr e = new E_NotExists(new OpTriple(t));
                                    ElementFilter filter = new ElementFilter(e);

                                    filters.add(filter);
                                    contNotExists++;

                                }

                                else {

                                    sparqlQuery.removeTriple(triple);
                                    return;

                                }

                            }

                            break;

                        case "notin":

                            if (objectVar) {

                                Expr e = new E_NotOneOf(new ExprVar(triple.getObject()), new ExprList(ac.getExprValues()));
                                ElementFilter filter = new ElementFilter(e);

                                filters.add(filter);

                            }

                            else if (objectLiteral && ac.contains(triple.getObject().getLiteralValue().toString())) {

                                if (predicateVar) {

                                    Triple t = new Triple(triple.getSubject(), predicateType, triple.getObject());
                                    Expr e = new E_NotExists(new OpTriple(t));
                                    ElementFilter filter = new ElementFilter(e);

                                    filters.add(filter);
                                    contNotExists++;

                                }

                                else {

                                    sparqlQuery.removeTriple(triple);
                                    return;

                                }

                            }

                            break;
                    }
                    
                }

            }
        }
        
        if (contNotExists != 0 && contNotExists == predicateTypes.size()*subjectTypes.size()-contSkip)
            sparqlQuery.removeTriple(triple);

        else {
            for (ElementFilter filter: filters)
                sparqlQuery.addFilter(filter);
        }
        
    }

}
