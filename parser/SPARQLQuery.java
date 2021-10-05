package parser;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.core.TriplePath;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.core.VarExprList;
import org.apache.jena.sparql.expr.*;
import org.apache.jena.sparql.pfunction.PropFuncArg;
import org.apache.jena.sparql.syntax.*;
import org.apache.jena.sparql.util.NodeUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * A class representing a SPARQL query that will be subjected to constraints
 *
 * inputQuery:          the initial query before the constraints checks
 * user:                the user or category of users that executed the query
 * varsTypes:           a list of all subject/object variables contained in the SPARQL query and their possible rdf:types
 * predicatesTypes:     a list of all predicate variables contained in the SPARQL query and their possible URI values
 * urisTypes:           a list of all uris contained in the SPARQL query and their possible rdf:types
 * outputQuery:         the final query after the constraints application
 */
public class SPARQLQuery {

    private final String user;
    private final Query inputQuery;
    private final ArrayList<VarTypes> varsTypes;
    private final ArrayList<PredicateTypes> predicatesTypes;
    private final ArrayList<UriTypes> urisTypes;
    private Query outputQuery;

    /**
     * Creates a SPARQL query object by passing a query string, a query user,
     * and a dataset on which the query has to be executed.
     *
     * inputQuery and outputQuery are instances of the Apache Jena Query class.
     * Elements of varsTypes, urisTypes and predicateTypes are obtained by executing the inputQuery on the model passed
     * as parameter adding the necessary triples (i.e. ?var rdf:type ?varType) and verifying the types resulted checking
     * the rdf schema also present in the dataset
     *
     * @param queryString a string representing the query
     * @param user a string representing the user that executed the query
     * @param model the dataset on which the query has to be executed
     */
    public SPARQLQuery(String queryString, String user, Model model) {

        this.user = user;
        inputQuery = QueryFactory.create(queryString);
        outputQuery = QueryFactory.create(queryString);

        System.out.println("--- USER: " + this.user + " ---");
        System.out.println("--- QUERY: ---");
        System.out.println(inputQuery);


        ArrayList<Var> vars = new ArrayList<>();
        ArrayList<Var> predicates = new ArrayList<>();
        ArrayList<Node> uris = new ArrayList<>();

        ElementWalker.walk(inputQuery.getQueryPattern(), new ElementVisitorBase() {

            @Override
            public void visit(ElementPathBlock el) {

                for (TriplePath t: el.getPattern().getList()) {

                    if (t.getSubject().isVariable()) {
                        if (!vars.contains(Var.alloc(t.getSubject())))
                            vars.add(Var.alloc(t.getSubject()));
                    }
                    else if (t.getSubject().isURI()) {
                        if (!uris.contains(t.getSubject()))
                            uris.add(t.getSubject());
                    }

                    if (t.getPredicate().isVariable()) {
                        if (!predicates.contains(Var.alloc(t.getPredicate())))
                            predicates.add(Var.alloc(t.getPredicate()));
                    }

                    if (t.getObject().isVariable()) {
                        if (!vars.contains(Var.alloc(t.getObject())))
                            vars.add(Var.alloc(t.getObject()));
                    }
                    else if (t.getObject().isURI()) {
                        if (!uris.contains(t.getObject()))
                            uris.add(t.getObject());
                    }

                }

            }
        });

        varsTypes = new ArrayList<>();

        for (Var v: vars) {
            VarTypes vt = new VarTypes(v, getVarTypes(v, model));
            varsTypes.add(vt);
        }
        
        urisTypes = new ArrayList<>();

        for (Node u: uris) {
            UriTypes ut = new UriTypes(u, getUriTypes(u, model));
            urisTypes.add(ut);
        }
        
        predicatesTypes = new ArrayList<>();

        for (Var p: predicates) {
            PredicateTypes pt = new PredicateTypes(p, getPredicateTypes(p, model));
            predicatesTypes.add(pt);
        }
        
    }


    // METHODS FOR OBTAINING TYPES OF VARIABLES, URIS AND PREDICATES ------------------------------------------------- //
    // getVarTypes
    // getUriTypes
    // getPredicateTypes
    // getTypes
    // getDomain
    // getRange
    // getSubClassesOf

    /**
     * Returns for a given variable the possible rdf:types
     *
     * The method first executes the inputQuery to find the possible rdf:types of the variable node.
     * Than check if all the result are correct by searching in the rdf schema of the  dataset, for all the predicates in
     * the query that refers to the variable, if the type is present in the field domain (if a subject) or range (if an object)
     *
     * @param v a variable whose rdf:types must be determined
     * @param model a model representing the dataset to use to find the variable rdf:types
     * @return a list of Node objects representing the rdf:types of the variable
     */
    private ArrayList<Node> getVarTypes(Var v, Model model) {

        ElementPathBlock block = new ElementPathBlock();
        block.addTriple(new Triple(v, NodeUtils.asNode("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), Var.alloc("type")));
        ArrayList<Node> result = getTypes(v.asNode(), block, "type", model);

        ArrayList<Node> types = new ArrayList<>();

        for (TriplePath t: getTriplesByVar(v)) {

            if (t.getPredicate().isURI()) {

                if (t.getSubject().equals(v))
                    types.addAll(getDomain(t.getPredicate(), model));

                else if (t.getObject().equals(v))
                    types.addAll(getRange(t.getPredicate(), model));

            }

        }

        result.removeIf(s -> !types.isEmpty() && !types.contains(s));

        return result;

    }

    /**
     * Returns for a given URI the possible rdf:types
     *
     * The method first executes the inputQuery to find the possible rdf:types of the URI node.
     * Than check if all the result are correct by searching in the rdf schema of the  dataset, for all the predicates in
     * the query that refers to the URI, if the type is present in the field domain (if a subject) or range (if an object)
     *
     * @param uri a URI whose rdf:types must be determined
     * @param model a model representing the dataset to use to find the URI rdf:types
     * @return a list of Node objects representing the rdf:types of the URI
     */
    private ArrayList<Node> getUriTypes(Node uri, Model model) {

        ElementPathBlock block = new ElementPathBlock();
        block.addTriple(new Triple(uri, NodeUtils.asNode("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), Var.alloc("type")));
        ArrayList<Node> result = getTypes(uri, block, "type", model);

        ArrayList<Node> types = new ArrayList<>();

        for (TriplePath t: getTriplesByUri(uri)) {

            if (t.getPredicate().isURI()) {

                if (t.getSubject().equals(uri))
                    types.addAll(getDomain(t.getPredicate(), model));

                else if (t.getObject().equals(uri))
                    types.addAll(getRange(t.getPredicate(), model));

            }

        }

        result.removeIf(s -> !types.isEmpty() && !types.contains(s));

        return result;

    }

    /**
     * Returns for a given variable the possible predicate values
     *
     * The method executes the inputQuery to find the possible values of the predicate variable.
     *
     * @param predicate the predicate var whose possible values must be determined
     * @param model a model representing the dataset to use to find the predicate values
     * @return a lsit of Node objects representing the possible values of the predicate
     */
    private ArrayList<Node> getPredicateTypes(Var predicate, Model model) {

        ElementPathBlock block = new ElementPathBlock();
        ArrayList<Node> result = getTypes(predicate.asNode(), block, predicate.toString(), model);
        
        if (!result.isEmpty())
            return result;


        // the second part is useless, i don't remember why i wrote it

        result = new ArrayList<>();
        
        for (TriplePath t: getTriplesByVar(predicate)) {
            
            Query q = QueryFactory.create();
            q.setQuerySelectType();
            q.addResultVar(predicate);
            q.setDistinct(true);

            ElementGroup body = new ElementGroup();

            block = new ElementPathBlock();
            block.addTriple(t.asTriple());
            
            if (t.getSubject().isVariable()) {

                block.addTriple(new Triple(t.getSubject(), NodeUtils.asNode("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), Var.alloc("subjectType")));

                ArrayList<Node> nodes = getNodeTypesByVar(t.getSubject());

                ExprList list = new ExprList();
                list.addAll(new PropFuncArg(nodes).asExprList());

                Expr e = new E_OneOf(new ExprVar(Var.alloc("subjectType")), list);
                ElementFilter filter = new ElementFilter(e);

                body.addElementFilter(filter);
                
            }

            if (t.getObject().isVariable()) {

                block.addTriple(new Triple(t.getObject(), NodeUtils.asNode("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), Var.alloc("objectType")));

                ArrayList<Node> nodes = getNodeTypesByVar(t.getObject());

                ExprList list = new ExprList();
                list.addAll(new PropFuncArg(nodes).asExprList());

                Expr e = new E_OneOf(new ExprVar(Var.alloc("objectType")), list);
                ElementFilter filter = new ElementFilter(e);

                body.addElementFilter(filter);

            }

            body.addElement(block);
            q.setQueryPattern(body);

            QueryExecution qe = QueryExecutionFactory.create(q, model);
            ResultSet rs = qe.execSelect();

            while (rs.hasNext()) {
                Node qs = NodeUtils.asNode(rs.next().get(predicate.getVarName()).toString());
                if (!result.contains(qs))
                    result.add(qs);
            }

        }

        return result;
        
    }

    /**
     * Executes the inputQuery adding a triple to obtain a specific value
     *
     * @param n the subject whose type must be find
     * @param block an ElementPathBlock that contain the additional triple containing the requested variable
     * @param resultString the string representing the variable to find
     * @param model the model representing the dataset to ask
     * @return a list of Node objects representing the node types / predicate value
     */
    private ArrayList<Node> getTypes(Node n, ElementPathBlock block, String resultString, Model model) {

        ArrayList<Node> result = new ArrayList<>();

        Query q = QueryFactory.create();
        q.setQuerySelectType();
        q.addResultVar(resultString);
        q.setDistinct(true);

        ElementGroup body = new ElementGroup();

        ElementWalker.walk(inputQuery.getQueryPattern(), new ElementVisitorBase() {

            @Override
            public void visit(ElementPathBlock el) {
                for (TriplePath t: el.getPattern().getList()) {
                    if (t.getSubject().equals(n) && t.getPredicate().equals(NodeUtils.asNode("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")) && t.getObject().isURI())
                        result.add(t.getObject());
                    block.addTriple(t.asTriple());
                }
                body.addElement(block);
            }

            @Override
            public void visit(ElementFilter el) {
                body.addElementFilter(el);
            }

        });

        if (!result.isEmpty())
            return result;

        q.setQueryPattern(body);
        QueryExecution qe = QueryExecutionFactory.create(q, model);
        ResultSet rs = qe.execSelect();

        while(rs.hasNext()) {
            Node sol = rs.next().get(resultString).asNode();
            result.add(sol);
        }

        return result;

    }

    /**
     * Returns the rdf:types obtained by the rdfs:domain attribute of a specified predicate passed as parameter.
     * The assumption is that the dataset contains a complete rdf schema.
     *
     * @param predicate the predicate to search in the rdf schema
     * @param model the model representing the dataset to ask
     * @return a list of Node objects representing the rdf:types of a specified subject node
     */
    public ArrayList<Node> getDomain(Node predicate, Model model) {

        ArrayList<Node> result = new ArrayList<>();

        Query q = QueryFactory.create();
        q.setQuerySelectType();
        q.addResultVar("domain");

        ElementPathBlock block = new ElementPathBlock();
        block.addTriple(new Triple(predicate, NodeUtils.asNode("http://www.w3.org/2000/01/rdf-schema#domain"), Var.alloc("domain")));

        q.setQueryPattern(block);

        QueryExecution qe = QueryExecutionFactory.create(q, model);
        ResultSet rs = qe.execSelect();

        while (rs.hasNext())
            result.add(rs.next().get("domain").asNode());

        if (!result.isEmpty())
            result.addAll(getSubClassesOf(result, model));

        return result;

    }

    /**
     * Returns the rdf:types obtained by the rdfs:range attribute of a specified predicate passed as parameter.
     * The assumption is that the dataset contains a complete rdf schema.
     *
     * @param predicate the predicate to search in the rdf schema
     * @param model the model representing the dataset to ask
     * @return a list of Node objects representing the rdf:types of a specified object node
     */
    public ArrayList<Node> getRange(Node predicate, Model model) {

        ArrayList<Node> result = new ArrayList<>();

        Query q = QueryFactory.create();
        q.setQuerySelectType();
        q.addResultVar("range");

        ElementPathBlock block = new ElementPathBlock();
        block.addTriple(new Triple(predicate, NodeUtils.asNode("http://www.w3.org/2000/01/rdf-schema#range"), Var.alloc("range")));

        q.setQueryPattern(block);

        QueryExecution qe = QueryExecutionFactory.create(q, model);
        ResultSet rs = qe.execSelect();

        while (rs.hasNext())
            result.add(rs.next().get("range").asNode());

        if (!result.isEmpty())
            result.addAll(getSubClassesOf(result, model));

        return result;

    }

    /**
     * Returns the subclasses of a list of rdf:types passed as parameters.
     * The assumption is that the dataset contains a complete rdf schema
     *
     * @param classes a list of Node objects representing the rdf:types whose subclasses must be determined
     * @param model the model representing the dataset to ask
     * @return a list of Node objects representing the rdf:types of subclasses of a node list
     */
    private ArrayList<Node> getSubClassesOf(ArrayList<Node> classes, Model model) {

        ArrayList<Node> result = new ArrayList<>();

        Query q = QueryFactory.create();
        q.setQuerySelectType();
        q.addResultVar("subClass");

        ElementPathBlock block;

        for (Node n: classes) {

            block = new ElementPathBlock();
            block.addTriple(new Triple(Var.alloc("subClass"), NodeUtils.asNode("http://www.w3.org/2000/01/rdf-schema#subClassOf"), n));

            q.setQueryPattern(block);

            QueryExecution qe = QueryExecutionFactory.create(q, model);
            ResultSet rs = qe.execSelect();

            while (rs.hasNext()) {
                result.add(rs.next().get("subClass").asNode());
            }

        }

        if (!result.isEmpty())
            result.addAll(getSubClassesOf(result, model));

        return result;

    }


    // METHODS FOR OBTAINING VARS, URIS, PREDICATES, TYPES, TRIPLES, FILTERS ----------------------------------------- //
    // getUser
    // getInputQuery
    // getOutputQuery
    // getNodeVars
    // getNodeUris
    // getNodeTypesByVar
    // getNodeTypesByUri
    // getPredicateTypesByVar
    // getTriples
    // getTriplesByVar
    // getTriplesByUri
    // getFilters
    // getVarsByFilter
    // getVarByExpr
    // getVarByAgg


    public String getUser() {
        return user;
    }

    public Query getInputQuery() {
        return inputQuery;
    }

    public Query getOutputQuery() {
        return outputQuery;
    }


    /**
     * @return a list of Var objects representing all the variables contained in the query
     */
    public ArrayList<Var> getNodeVars() {

        ArrayList<Var> result = new ArrayList<>();

        for (VarTypes vt: varsTypes)
            result.add(vt.getVar());

        return result;

    }

    /**
     * @return a list of Node objects representing all the URIs contained in the query
     */
    public ArrayList<Node> getNodeUris() {

        ArrayList<Node> result = new ArrayList<>();

        for (UriTypes ut: urisTypes)
            result.add(ut.getUri());

        return result;

    }

    /**
     * @param n a node whose rdf:types must be returned
     * @return a list of Node objects representing the node types of the specified node
     */
    public ArrayList<Node> getNodeTypesByVar(Node n) {
        return getNodeTypesByVar(Var.alloc(n));
    }

    /**
     * @param v a variable whose rdf:types must be returned
     * @return a list of Node objects representing the node types of the specified node
     */
    public ArrayList<Node> getNodeTypesByVar(Var v) {

        for (VarTypes vt: varsTypes) {
            if (vt.getVar().equals(v))
                return vt.getTypes();
        }

        return new ArrayList<>();

    }

    /**
     * @param uri a URI node whose rdf:types must be returned
     * @return a list of Node objects representing the node types of the specified URI
     */
    public ArrayList<Node> getNodeTypesByUri(Node uri) {

        for (UriTypes ut: urisTypes) {
            if (ut.getUri().equals(uri))
                return ut.getTypes();
        }

        return new ArrayList<>();

    }

    /**
     * @param predicate a predicate variable whose rdf:types must be returned
     * @return a list of Node objects representing the predicate value of the specified variable
     */
    public ArrayList<Node> getPredicateTypesByVar(Node predicate) {
        return getPredicateTypesByVar(Var.alloc(predicate));
    }

    /**
     * @param predicate a predicate variable whose rdf:types must be returned
     * @return a list of Node objects representing the predicate value of the specified variable
     */
    public ArrayList<Node> getPredicateTypesByVar(Var predicate) {

        for (PredicateTypes pt: predicatesTypes) {
            if (pt.getPredicate().equals(predicate))
                return pt.getTypes();
        }

        return new ArrayList<>();

    }

    /**
     * @return a list of TriplePath objects representing all the triples in the query
     */
    public ArrayList<TriplePath> getTriples() {

        ArrayList<TriplePath> result = new ArrayList<>();

        ElementWalker.walk(outputQuery.getQueryPattern(), new ElementVisitorBase() {

            @Override
            public void visit(ElementPathBlock el) {
                result.addAll(el.getPattern().getList());
            }

        });

        return result;

    }

    /**
     * @param v the variable whose filters must be returned
     * @return a list of TriplePath objects representing all the triples in the query that contains the specified variable
     */
    public ArrayList<TriplePath> getTriplesByVar(Var v) {

        ArrayList<TriplePath> result = new ArrayList<>();

        for (TriplePath t: getTriples()) {
            if (t.getSubject().equals(v) || t.getPredicate().equals(v) || t.getObject().equals(v))
                result.add(t);
        }

        return result;

    }

    /**
     * @param uri the URI whose filters must be returned
     * @return a list of TriplePath objects representing all the triples in the query that contains the specified URI
     */
    public ArrayList<TriplePath> getTriplesByUri(Node uri) {

        ArrayList<TriplePath> result = new ArrayList<>();

        for (TriplePath t: getTriples()) {
            if (t.getSubject().equals(uri) || t.getPredicate().equals(uri) || t.getObject().equals(uri))
                result.add(t);
        }

        return result;

    }

    /**
     * @return a list of ElementFilter objects representing all the filters contained in the query
     */
    private ArrayList<ElementFilter> getFilters() {

        ArrayList<ElementFilter> result = new ArrayList<>();

        ElementWalker.walk(outputQuery.getQueryPattern(), new ElementVisitorBase() {

            @Override
            public void visit(ElementFilter el) {
                result.add(el);
            }

        });

        return result;

    }

    /**
     * @param filter a filter whose variable contained must be returned
     * @return a list of Var objects representing all the variables contained in the specified filter
     */
    private ArrayList<Var> getVarsByFilter(ElementFilter filter) {

        ArrayList<Var> result = new ArrayList<>();

        for (Var v: getVarsByFunc(filter.getExpr().getFunction())) {
            if (!result.contains(v))
                result.add(v);
        }

        return result;

    }

    /**
     * @param expr an Expr object whose variable contained must be returned
     * @return a list of Var objects representing all the variables contained in the specified Expr object
     */
    private ArrayList<Var> getVarsByFunc(ExprFunction expr) {

        ArrayList<Var> result = new ArrayList<>();

        if (expr.getFunctionSymbol().getSymbol().equals("notexists")) {
            String var = expr.getGraphPattern().toString().split(" ")[1].replace("?", "");
            result.add(Var.alloc(var));
            return result;
        }

        for (Expr e: expr.getArgs()) {

            if (e.isVariable())
                result.add(e.asVar());

            else if (e.isFunction())
                result.addAll(getVarsByFunc(e.getFunction()));

            else if (e.toString().contains("AGG "))
                result.addAll(getVarsByAgg((ExprAggregator) e));

        }

        return result;

    }

    /**
     * @param agg an aggregator whose variable contained must be returned
     * @return a list of Var objects representing all the variables contained in the specified aggregator
     */
    private ArrayList<Var> getVarsByAgg(ExprAggregator agg) {

        ArrayList<Var> result = new ArrayList<>();

        for (Expr e: agg.getAggregator().getExprList().getList()) {

            if (e.isVariable())
                result.add(e.asVar());

            else if (e.isFunction())
                result.addAll(getVarsByFunc(e.getFunction()));

        }

        return result;

    }


    // METHODS FOR MODIFYING THE QUERY ELEMENTS ---------------------------------------------------------------------- //
    // removeResultVar
    // removeTriple
    // removeFilter
    // addFilter
    // combineFilters
    // compareNodeValues
    // removeResultVarsNotInTriples
    // removeFiltersWithVarsNotInTriples
    // removeVarsUrisPredicatesNotInQuery

    /**
     * Remove the specified variable passed as parameter from the result variables of the query
     *
     * @param var the variable to remove
     */
    public void removeResultVar(Var var) {

        Query q = QueryFactory.create();
        q.setQuerySelectType();

        q.setPrefixMapping(outputQuery.getPrefixMapping());

        VarExprList project = outputQuery.getProject();

        for (Var v: project.getVars()) {

            if (v.equals(var)) {
                System.out.println("Remove result var: " + v);
                continue;
            }

            if (project.hasExpr(v)) {

                ArrayList<Var> vars;

                if (project.getExpr(v).isFunction())
                    vars = getVarsByFunc(project.getExpr(v).getFunction());
                else
                    vars = getVarsByAgg((ExprAggregator) project.getExpr(v));

                if (vars.contains(var)) {
                    System.out.println("Remove result var: " + v + " = " + project.getExpr(v));
                    continue;
                }

            }

            if (project.hasExpr(v))
                q.addResultVar(v, project.getExpr(v));
            else
                q.addResultVar(v);

        }

        q.setDistinct(outputQuery.isDistinct());

        ElementPathBlock block = new ElementPathBlock();
        for (TriplePath t: getTriples())
            block.addTriple(t.asTriple());

        ElementGroup body = new ElementGroup();
        body.addElement(block);

        for (ElementFilter filter: getFilters())
            body.addElementFilter(filter);

        q.setQueryPattern(body);

        if (outputQuery.hasGroupBy()) {
            VarExprList groupBy = outputQuery.getGroupBy();
            for (Var v : groupBy.getVars()) {
                if (groupBy.hasExpr(v))
                    q.addGroupBy(v, groupBy.getExpr(v));
                else
                    q.addGroupBy(v);
            }
        }

        if (outputQuery.hasHaving()) {
            for (Expr e: outputQuery.getHavingExprs())
                q.addHavingCondition(e);
        }

        if (outputQuery.hasLimit())
            q.setLimit(outputQuery.getLimit());

        if (outputQuery.hasOffset())
            q.setOffset(outputQuery.getOffset());

        if (outputQuery.hasOrderBy()) {
            for (SortCondition sc: outputQuery.getOrderBy())
                q.addOrderBy(sc);
        }

        outputQuery = q;

    }

    /**
     * Remove a specified triple passed as parameter from the query pattern
     *
     * @param triple the triple to remove
     */
    public void removeTriple(TriplePath triple) {

        System.out.println("Remove triple: " + triple);

        ElementPathBlock block = new ElementPathBlock();
        
        for (TriplePath t: getTriples()) {
            if (!t.equals(triple))
                block.addTriple(t.asTriple());
        }
        
        ElementGroup body = new ElementGroup();
        body.addElement(block);

        for (ElementFilter f: getFilters()) {
            body.addElementFilter(f);
        }

        outputQuery.setQueryPattern(body);

        removeResultVarsNotInTriples();
        removeVarsUrisPredicatesNotInQuery();
        removeFiltersWithVarsNotInTriples();

    }

    /**
     * Remove all the result variables that do not compare anymore in the query pattern.
     * The void is executed every time a triple is removed from the query pattern.
     */
    public void removeResultVarsNotInTriples() {

        ArrayList<Var> vars = new ArrayList<>();
        VarExprList project = outputQuery.getProject();

        for (Var v: project.getVars()) {

            if (project.hasExpr(v)) {
                if (project.getExpr(v).isFunction())
                    vars.addAll(getVarsByFunc(project.getExpr(v).getFunction()));
                else
                    vars.addAll(getVarsByAgg((ExprAggregator) project.getExpr(v)));
            }
            else {
                vars.add(v);
            }

        }

        for (Var v: vars) {
            if (getTriplesByVar(v).isEmpty())
                removeResultVar(v);
        }

    }

    /**
     * Remove all teh variable, uris and predicates from the object fileds varsTypes, urisTypes and predicateTypes.
     * The void is executed every time a triple is removed from the query pattern.
     */
    private void removeVarsUrisPredicatesNotInQuery() {

        ArrayList<Var> vars = new ArrayList<>();
        ArrayList<Var> predicates = new ArrayList<>();
        ArrayList<Node> uris = new ArrayList<>();

        ElementWalker.walk(outputQuery.getQueryPattern(), new ElementVisitorBase() {

            @Override
            public void visit(ElementPathBlock el) {

                for (TriplePath t: el.getPattern().getList()) {

                    if (t.getSubject().isVariable())
                        vars.add(Var.alloc(t.getSubject()));
                    else if (t.getSubject().isURI())
                        uris.add(t.getSubject());

                    if (t.getPredicate().isVariable())
                        predicates.add(Var.alloc(t.getPredicate()));

                    if (t.getObject().isVariable())
                        vars.add(Var.alloc(t.getObject()));
                    else if (t.getObject().isURI())
                        uris.add(t.getObject());

                }

            }

        });

        varsTypes.removeIf(vt -> !vars.contains(vt.getVar()));
        predicatesTypes.removeIf(pt -> !predicates.contains(pt.getPredicate()));
        urisTypes.removeIf(ut -> !uris.contains(ut.getUri()));

    }

    /**
     * Remove all the filters which have variables that do not compare anymore in the query pattern.
     * The void is executed every time a triple is removed from the query pattern.
     */
    private void removeFiltersWithVarsNotInTriples() {

        ArrayList<ElementFilter> filtersToRemove = new ArrayList<>();

        for (ElementFilter filter: getFilters()) {

            for (Var v: getVarsByFilter(filter)) {

                boolean exists = false;

                for (VarTypes vt: varsTypes) {
                    if (vt.getVar().equals(v)) {
                        exists = true;
                        break;
                    }
                }

                for (PredicateTypes pt: predicatesTypes) {
                    if (pt.getPredicate().equals(v)) {
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    filtersToRemove.add(filter);
                    break;
                }

            }

        }

        for (ElementFilter filter: filtersToRemove)
            removeFilter(filter);

    }

    /**
     * Remove a specified filter passed as parameter from the query pattern
     *
     * @param filter the filter to remove
     */
    private void removeFilter(ElementFilter filter) {

        System.out.println("Remove filter: " + filter);

        ElementPathBlock block = new ElementPathBlock();

        for (TriplePath t: getTriples()) {
            block.addTriple(t.asTriple());
        }

        ElementGroup body = new ElementGroup();
        body.addElement(block);

        for (ElementFilter f: getFilters()) {
            if (f.getExpr().getFunction().getFunctionSymbol().getSymbol().equals("notexists") && filter.getExpr().getFunction().getFunctionSymbol().getSymbol().equals("notexists"))
                if (!f.getExpr().getFunction().getGraphPattern().equals(filter.getExpr().getFunction().getGraphPattern()))
                    body.addElementFilter(f);
            else if (!f.equals(filter))
                body.addElementFilter(f);
        }

        outputQuery.setQueryPattern(body);

    }

    /**
     * Add a filter to the query pattern.
     * If the filter already exists the void does not add the filter.
     * If there are already other filters of the same variable, the void add a combined filter if the filters are not contradictory,
     * otherwise it removes the filter and the triples containing that variable object node
     *
     * @param filter the filter to add
     */
    public void addFilter(ElementFilter filter) {
        
        ElementPathBlock block = new ElementPathBlock();
        
        for (TriplePath t: getTriples())
            block.addTriple(t.asTriple());

        ElementGroup body = new ElementGroup();
        body.addElement(block);

        boolean filterAlreadyExists = false;
        ArrayList<ElementFilter> filters = new ArrayList<>();

        boolean moreVariables = getVarsByFilter(filter).size() > 1;
        Var v = getVarsByFilter(filter).get(0);

        boolean predicateVar = false;
        for (TriplePath t: getTriplesByVar(v)) {
            if (t.getObject().equals(v) && t.getPredicate().isVariable())
                predicateVar = true;
        }

        for (ElementFilter f: getFilters()) {

            if (f.getExpr().getFunction().getFunctionSymbol().getSymbol().equals("notexists") && filter.getExpr().getFunction().getFunctionSymbol().getSymbol().equals("notexists")) {
                if (f.getExpr().getFunction().getGraphPattern().equals(filter.getExpr().getFunction().getGraphPattern()))
                    filterAlreadyExists = true;
            }
            else if (f.equals(filter))
                filterAlreadyExists = true;

            if (f.getExpr().getFunction().getFunctionSymbol().getSymbol().equals("notexists") || filter.getExpr().getFunction().getFunctionSymbol().getSymbol().equals("notexists")) {
                body.addElementFilter(f);
                continue;
            }

            if (!predicateVar && !moreVariables && getVarsByFilter(f).size() == 1 && getVarsByFilter(f).get(0).equals(v))
                filters.add(f);
            else
                body.addElementFilter(f);

        }

        boolean removeTriple = false;

        if ( (filters.size() == 0 || filter.getExpr().getFunction().getFunctionSymbol().getSymbol().equals("notexists") ) && !filterAlreadyExists ) {
            body.addElementFilter(filter);
            System.out.println("Add filter: " + filter);
        }
        else if (filters.size() == 1 && filterAlreadyExists) {
            System.out.println("Filter already exists: " + filters.get(0));
            body.addElementFilter(filters.get(0));
        }
        else {

            for (ElementFilter f: filters)
                System.out.println("Remove filter: " + f);

            if (!filterAlreadyExists)
                filters.add(filter);

            ElementFilter combinedFilter = combineFilters(filters);

            if (combinedFilter != null) {
                body.addElementFilter(combinedFilter);
                System.out.println("Add combined filter: " + combinedFilter);
            }
            else
                removeTriple = true;

        }

        outputQuery.setQueryPattern(body);

        if (removeTriple) {
            for (TriplePath t : getTriplesByVar(v)) {
                if (t.getObject().equals(v))
                    removeTriple(t);
            }
        }

    }

    /**
     * Combines a list of filters passed as parameters.
     * The void combines only simple filters (variable operator values).
     * Complex filters are only combined with a logical AND operator.
     * If the filters are contradictory, the void return a null result.
     *
     * @param filters the list of filters to be combined
     * @return an ElementFilter object representing the combined filter
     *         or a null value if the filters can not be combined
     */
    private ElementFilter combineFilters(ArrayList<ElementFilter> filters) {

        Expr expr = filters.get(0).getExpr();

        for (int i=1; i<filters.size(); i++) {

            Expr e = filters.get(i).getExpr();

            if (expr == null)
                return null;

            ExprFunction f1 = expr.getFunction();
            ExprFunction f2 = e.getFunction();
            String symbol1 = f1.getFunctionSymbol().getSymbol();
            String symbol2 = f2.getFunctionSymbol().getSymbol();

            ExprVar variable = null;
            ArrayList<NodeValue> values1 = new ArrayList<>();
            for (Expr v: f1.getArgs()) {
                if (v.isConstant())
                    values1.add(v.getConstant());
                else if (v.isVariable())
                    variable = v.getExprVar();
            }
            ArrayList<NodeValue> values2 = new ArrayList<>();
            for (Expr v: f2.getArgs()) {
                if (v.isConstant())
                    values2.add(v.getConstant());
            }


            if (symbol1.equals("and") || symbol1.equals("or") || symbol1.equals("not") || symbol2.equals("and") || symbol2.equals("or") || symbol2.equals("not"))
                expr = new E_LogicalAnd(expr, e);
            else {

                switch (symbol1) {

                    case "eq":

                        switch (symbol2) {

                            case "eq":
                                if (compareNodeValues(values1.get(0), values2.get(0)) != 0)
                                    expr = null;
                                break;

                            case "ne":
                                if (compareNodeValues(values1.get(0), values2.get(0)) == 0)
                                    expr = null;
                                break;

                            case "gt":
                                if (compareNodeValues(values1.get(0), values2.get(0)) <= 0)
                                    expr = null;
                                break;

                            case "ge":
                                if (compareNodeValues(values1.get(0), values2.get(0)) < 0)
                                    expr = null;
                                break;

                            case "lt":
                                if (compareNodeValues(values1.get(0), values2.get(0)) >= 0)
                                    expr = null;
                                break;

                            case "le":
                                if (compareNodeValues(values1.get(0), values2.get(0)) > 0)
                                    expr = null;
                                break;

                            case "in":
                                boolean in = false;
                                for (NodeValue nv: values2) {
                                    if (compareNodeValues(values1.get(0), nv) == 0)
                                        in = true;
                                }
                                if (!in)
                                    expr = null;
                                break;

                            case "notin":
                                for (NodeValue nv: values2) {
                                    if (compareNodeValues(values1.get(0), nv) == 0)
                                        expr = null;
                                }
                                break;

                        }

                        break;

                    case "ne":

                        switch (symbol2) {

                            case "eq":
                                if (compareNodeValues(values1.get(0), values2.get(0)) == 0)
                                    expr = null;
                                else
                                    expr = e;
                                break;

                            case "ne":
                                if (compareNodeValues(values1.get(0), values2.get(0)) != 0) {
                                    ArrayList<Node> listNotEq = new ArrayList<>();
                                    listNotEq.add(values1.get(0).asNode());
                                    listNotEq.add(values2.get(0).asNode());
                                    expr = new E_NotOneOf(variable, new PropFuncArg(listNotEq).asExprList());
                                }
                                break;

                            case "gt":
                                if (compareNodeValues(values1.get(0), values2.get(0)) <= 0)
                                    expr = e;
                                else
                                    expr = new E_LogicalAnd(expr, e);
                                break;

                            case "ge":
                                if (compareNodeValues(values1.get(0), values2.get(0)) < 0)
                                    expr = e;
                                else if (compareNodeValues(values1.get(0), values2.get(0)) == 0)
                                    expr = new E_GreaterThan(variable, values2.get(0));
                                else
                                    expr = new E_LogicalAnd(expr, e);
                                break;

                            case "lt":
                                if (compareNodeValues(values1.get(0), values2.get(0)) >= 0)
                                    expr = e;
                                else
                                    expr = new E_LogicalAnd(expr, e);
                                break;

                            case "le":
                                if (compareNodeValues(values1.get(0), values2.get(0)) > 0)
                                    expr = e;
                                else if (compareNodeValues(values1.get(0), values2.get(0)) == 0)
                                    expr = new E_LessThan(variable, values2.get(0));
                                else
                                    expr = new E_LogicalAnd(expr, e);
                                break;

                            case "in":
                                ArrayList<Node> listIn = new ArrayList<>();
                                NodeValue x = null;
                                for (NodeValue nv: values2) {
                                    if (compareNodeValues(values1.get(0), nv) != 0) {
                                        listIn.add(nv.asNode());
                                        x = nv;
                                    }
                                }
                                if (listIn.isEmpty())
                                    expr = null;
                                else if (listIn.size() == 1)
                                    expr = new E_Equals(variable, x);
                                else
                                    expr = new E_OneOf(variable, new PropFuncArg(listIn).asExprList());
                                break;

                            case "notin":
                                ArrayList<Node> listNotIn = new ArrayList<>();
                                boolean valueInList = false;
                                for (NodeValue nv: values2) {
                                    listNotIn.add(nv.asNode());
                                    if (compareNodeValues(values1.get(0), nv) == 0)
                                        valueInList = true;
                                }
                                if (!listNotIn.isEmpty()) {
                                    if (!valueInList)
                                        listNotIn.add(values1.get(0).asNode());
                                    if (listNotIn.size() > 1)
                                        expr = new E_NotOneOf(variable, new PropFuncArg(listNotIn).asExprList());
                                }
                                break;

                        }

                        break;

                    case "gt":

                        switch (symbol2) {

                            case "eq":
                                if (compareNodeValues(values1.get(0), values2.get(0)) < 0)
                                    expr = e;
                                else
                                    expr = null;
                                break;

                            case "ne":
                                if (compareNodeValues(values1.get(0), values2.get(0)) < 0)
                                    expr = new E_LogicalAnd(expr, e);
                                break;

                            case "gt":

                            case "ge":
                                if (compareNodeValues(values1.get(0), values2.get(0)) < 0)
                                    expr = e;
                                break;

                            case "lt":

                            case "le":
                                if (compareNodeValues(values1.get(0), values2.get(0)) < 0)
                                    expr = new E_LogicalAnd(expr, e);
                                else
                                    expr = null;
                                break;

                            case "in":
                                ArrayList<Node> listIn = new ArrayList<>();
                                NodeValue x1 = null;
                                for (NodeValue nv: values2) {
                                    if (compareNodeValues(values1.get(0), nv) < 0) {
                                        listIn.add(nv.asNode());
                                        x1 = nv;
                                    }
                                }
                                if (listIn.isEmpty())
                                    expr = null;
                                else if (listIn.size() == 1)
                                    expr = new E_Equals(variable, x1);
                                else
                                    expr = new E_OneOf(variable, new PropFuncArg(listIn).asExprList());
                                break;

                            case "notin":
                                ArrayList<Node> listNotIn = new ArrayList<>();
                                NodeValue x2 = null;
                                for (NodeValue nv: values2) {
                                    if (compareNodeValues(values1.get(0), nv) < 0) {
                                        listNotIn.add(nv.asNode());
                                        x2 = nv;
                                    }
                                }
                                if (listNotIn.size() == 1)
                                    expr = new E_LogicalAnd(expr, new E_NotEquals(variable, x2));
                                else if (listNotIn.size() > 1)
                                    expr = new E_LogicalAnd(expr, new E_NotOneOf(variable, new PropFuncArg(listNotIn).asExprList()));
                                break;

                        }

                        break;

                    case "ge":

                        switch (symbol2) {

                            case "eq":
                                if (compareNodeValues(values1.get(0), values2.get(0)) <= 0)
                                    expr = e;
                                else
                                    expr = null;
                                break;

                            case "ne":
                                if (compareNodeValues(values1.get(0), values2.get(0)) < 0)
                                    expr = new E_LogicalAnd(expr, e);
                                else if (compareNodeValues(values1.get(0), values2.get(0)) == 0)
                                    expr = new E_GreaterThan(variable, values1.get(0));
                                break;

                            case "gt":
                                if (compareNodeValues(values1.get(0), values2.get(0)) <= 0)
                                    expr = e;
                                break;

                            case "ge":
                                if (compareNodeValues(values1.get(0), values2.get(0)) < 0)
                                    expr = e;
                                break;

                            case "lt":
                                if (compareNodeValues(values1.get(0), values2.get(0)) < 0)
                                    expr = new E_LogicalAnd(expr, e);
                                else
                                    expr = null;
                                break;

                            case "le":
                                if (compareNodeValues(values1.get(0), values2.get(0)) < 0)
                                    expr = new E_LogicalAnd(expr, e);
                                else if (compareNodeValues(values1.get(0), values2.get(0)) == 0)
                                    expr = new E_Equals(variable, values1.get(0));
                                else
                                    expr = null;
                                break;

                            case "in":
                                ArrayList<Node> listIn = new ArrayList<>();
                                NodeValue x1 = null;
                                for (NodeValue nv: values2) {
                                    if (compareNodeValues(values1.get(0), nv) <= 0) {
                                        listIn.add(nv.asNode());
                                        x1 = nv;
                                    }
                                }
                                if (listIn.isEmpty())
                                    expr = null;
                                else if (listIn.size() == 1)
                                    expr = new E_Equals(variable, x1);
                                else
                                    expr = new E_OneOf(variable, new PropFuncArg(listIn).asExprList());
                                break;

                            case "notin":
                                ArrayList<Node> listNotIn = new ArrayList<>();
                                NodeValue x2 = null;
                                boolean notEqual = false;
                                for (NodeValue nv: values2) {
                                    if (compareNodeValues(values1.get(0), nv) < 0) {
                                        listNotIn.add(nv.asNode());
                                        x2 = nv;
                                    }
                                    else if (compareNodeValues(values1.get(0), nv) == 0)
                                        notEqual = true;
                                }
                                if (notEqual)
                                    expr = new E_GreaterThan(variable, values1.get(0));
                                if (listNotIn.size() == 1)
                                    expr = new E_LogicalAnd(expr, new E_NotEquals(variable, x2));
                                else if (listNotIn.size() > 1)
                                    expr = new E_LogicalAnd(expr, new E_NotOneOf(variable, new PropFuncArg(listNotIn).asExprList()));
                                break;

                        }

                        break;

                    case "lt":

                        switch (symbol2) {

                            case "eq":
                                if (compareNodeValues(values1.get(0), values2.get(0)) > 0)
                                    expr = e;
                                else
                                    expr = null;
                                break;

                            case "ne":
                                if (compareNodeValues(values1.get(0), values2.get(0)) > 0)
                                    expr = new E_LogicalAnd(expr, e);
                                break;

                            case "gt":

                            case "ge":
                                if (compareNodeValues(values1.get(0), values2.get(0)) > 0)
                                    expr = new E_LogicalAnd(expr, e);
                                else
                                    expr = null;
                                break;

                            case "lt":

                            case "le":
                                if (compareNodeValues(values1.get(0), values2.get(0)) > 0)
                                    expr = e;
                                break;

                            case "in":
                                ArrayList<Node> listIn = new ArrayList<>();
                                NodeValue x1 = null;
                                for (NodeValue nv: values2) {
                                    if (compareNodeValues(values1.get(0), nv) > 0) {
                                        listIn.add(nv.asNode());
                                        x1 = nv;
                                    }
                                }
                                if (listIn.isEmpty())
                                    expr = null;
                                else if (listIn.size() == 1)
                                    expr = new E_Equals(variable, x1);
                                else
                                    expr = new E_OneOf(variable, new PropFuncArg(listIn).asExprList());
                                break;

                            case "notin":
                                ArrayList<Node> listNotIn = new ArrayList<>();
                                NodeValue x2 = null;
                                for (NodeValue nv: values2) {
                                    if (compareNodeValues(values1.get(0), nv) > 0) {
                                        listNotIn.add(nv.asNode());
                                        x2 = nv;
                                    }
                                }
                                if (listNotIn.size() == 1)
                                    expr = new E_LogicalAnd(expr, new E_NotEquals(variable, x2));
                                else if (listNotIn.size() > 1)
                                    expr = new E_LogicalAnd(expr, new E_NotOneOf(variable, new PropFuncArg(listNotIn).asExprList()));
                                break;

                        }

                        break;

                    case "le":

                        switch (symbol2) {

                            case "eq":
                                if (compareNodeValues(values1.get(0), values2.get(0)) >= 0)
                                    expr = e;
                                else
                                    expr = null;
                                break;

                            case "ne":
                                if (compareNodeValues(values1.get(0), values2.get(0)) > 0)
                                    expr = new E_LogicalAnd(expr, e);
                                else if (compareNodeValues(values1.get(0), values2.get(0)) == 0)
                                    expr = new E_LessThan(variable, values1.get(0));
                                break;

                            case "gt":
                                if (compareNodeValues(values1.get(0), values2.get(0)) > 0)
                                    expr = new E_LogicalAnd(expr, e);
                                else
                                    expr = null;
                                break;

                            case "ge":
                                if (compareNodeValues(values1.get(0), values2.get(0)) > 0)
                                    expr = new E_LogicalAnd(expr, e);
                                else if (compareNodeValues(values1.get(0), values2.get(0)) == 0)
                                    expr = new E_Equals(variable, values1.get(0));
                                else
                                    expr = null;
                                break;

                            case "lt":
                                if (compareNodeValues(values1.get(0), values2.get(0)) >= 0)
                                    expr = e;
                                break;

                            case "le":
                                if (compareNodeValues(values1.get(0), values2.get(0)) > 0)
                                    expr = e;
                                break;

                            case "in":
                                ArrayList<Node> listIn = new ArrayList<>();
                                NodeValue x1 = null;
                                for (NodeValue nv: values2) {
                                    if (compareNodeValues(values1.get(0), nv) >= 0) {
                                        listIn.add(nv.asNode());
                                        x1 = nv;
                                    }
                                }
                                if (listIn.isEmpty())
                                    expr = null;
                                else if (listIn.size() == 1)
                                    expr = new E_Equals(variable, x1);
                                else
                                    expr = new E_OneOf(variable, new PropFuncArg(listIn).asExprList());
                                break;

                            case "notin":
                                ArrayList<Node> listNotIn = new ArrayList<>();
                                NodeValue x2 = null;
                                boolean notEqual = false;
                                for (NodeValue nv: values2) {
                                    if (compareNodeValues(values1.get(0), nv) > 0) {
                                        listNotIn.add(nv.asNode());
                                        x2 = nv;
                                    }
                                    else if (compareNodeValues(values1.get(0), nv) == 0)
                                        notEqual = true;
                                }
                                if (notEqual)
                                    expr = new E_LessThan(variable, values1.get(0));
                                if (listNotIn.size() == 1)
                                    expr = new E_LogicalAnd(expr, new E_NotEquals(variable, x2));
                                else if (listNotIn.size() > 1)
                                    expr = new E_LogicalAnd(expr, new E_NotOneOf(variable, new PropFuncArg(listNotIn).asExprList()));
                                break;

                        }

                        break;

                    case "in":

                        switch (symbol2) {

                            case "eq":
                                expr = null;
                                for (NodeValue nv: values1) {
                                    if (compareNodeValues(nv, values2.get(0)) == 0) {
                                        expr = e;
                                    }
                                }
                                break;

                            case "ne":
                                ArrayList<Node> listNotEqual = new ArrayList<>();
                                NodeValue x1 = null;
                                for (NodeValue nv: values1) {
                                    if (compareNodeValues(nv, values2.get(0)) != 0) {
                                        listNotEqual.add(nv.asNode());
                                        x1 = nv;
                                    }
                                }
                                if (listNotEqual.isEmpty())
                                    expr = null;
                                else if (listNotEqual.size() == 1)
                                    expr = new E_Equals(variable, x1);
                                else
                                    expr = new E_OneOf(variable, new PropFuncArg(listNotEqual).asExprList());
                                break;

                            case "gt":
                                ArrayList<Node> listGreater = new ArrayList<>();
                                NodeValue x2 = null;
                                for (NodeValue nv: values1) {
                                    if (compareNodeValues(nv, values2.get(0)) > 0) {
                                        listGreater.add(nv.asNode());
                                        x2 = nv;
                                    }
                                }
                                if (listGreater.isEmpty())
                                    expr = null;
                                else if (listGreater.size() == 1)
                                    expr = new E_Equals(variable, x2);
                                else
                                    expr = new E_OneOf(variable, new PropFuncArg(listGreater).asExprList());
                                break;

                            case "ge":
                                ArrayList<Node> listGreaterEqual = new ArrayList<>();
                                NodeValue x3 = null;
                                for (NodeValue nv: values1) {
                                    if (compareNodeValues(nv, values2.get(0)) >= 0) {
                                        listGreaterEqual.add(nv.asNode());
                                        x3 = nv;
                                    }
                                }
                                if (listGreaterEqual.isEmpty())
                                    expr = null;
                                else if (listGreaterEqual.size() == 1)
                                    expr = new E_Equals(variable, x3);
                                else
                                    expr = new E_OneOf(variable, new PropFuncArg(listGreaterEqual).asExprList());
                                break;

                            case "lt":
                                ArrayList<Node> listLess = new ArrayList<>();
                                NodeValue x4 = null;
                                for (NodeValue nv: values1) {
                                    if (compareNodeValues(nv, values2.get(0)) < 0) {
                                        listLess.add(nv.asNode());
                                        x4 = nv;
                                    }
                                }
                                if (listLess.isEmpty())
                                    expr = null;
                                else if (listLess.size() == 1)
                                    expr = new E_Equals(variable, x4);
                                else
                                    expr = new E_OneOf(variable, new PropFuncArg(listLess).asExprList());
                                break;

                            case "le":
                                ArrayList<Node> listLessEqual = new ArrayList<>();
                                NodeValue x5 = null;
                                for (NodeValue nv: values1) {
                                    if (compareNodeValues(nv, values2.get(0)) <= 0) {
                                        listLessEqual.add(nv.asNode());
                                        x5 = nv;
                                    }
                                }
                                if (listLessEqual.isEmpty())
                                    expr = null;
                                else if (listLessEqual.size() == 1)
                                    expr = new E_Equals(variable, x5);
                                else
                                    expr = new E_OneOf(variable, new PropFuncArg(listLessEqual).asExprList());
                                break;

                            case "in":
                                ArrayList<Node> listIn = new ArrayList<>();
                                NodeValue x6 = null;
                                for (NodeValue nv1: values1) {
                                    for (NodeValue nv2: values2) {
                                        if (compareNodeValues(nv1, nv2) == 0) {
                                            listIn.add(nv1.asNode());
                                            x6 = nv1;
                                        }
                                    }
                                }
                                if (listIn.isEmpty())
                                    expr = null;
                                else if (listIn.size() == 1)
                                    expr = new E_Equals(variable, x6);
                                else
                                    expr = new E_OneOf(variable, new PropFuncArg(listIn).asExprList());
                                break;

                            case "notin":
                                ArrayList<Node> listNotIn = new ArrayList<>();
                                NodeValue x7 = null;
                                for (NodeValue nv1: values1) {
                                    boolean in = true;
                                    for (NodeValue nv2: values2) {
                                        if (compareNodeValues(nv1, nv2) == 0)
                                            in = false;
                                    }
                                    if (in) {
                                        listNotIn.add(nv1.asNode());
                                        x7 = nv1;
                                    }
                                }
                                if (listNotIn.isEmpty())
                                    expr = null;
                                else if (listNotIn.size() == 1)
                                    expr = new E_Equals(variable, x7);
                                else
                                    expr = new E_OneOf(variable, new PropFuncArg(listNotIn).asExprList());
                                break;

                        }

                        break;

                    case "notin":

                        switch (symbol2) {

                            case "eq":
                                expr = e;
                                for (NodeValue nv: values1) {
                                    if (compareNodeValues(nv, values2.get(0)) == 0)
                                        expr = null;
                                }
                                break;

                            case "ne":
                                ArrayList<Node> listNotEqual = new ArrayList<>();
                                boolean valueInList = false;
                                for (NodeValue nv: values1) {
                                    listNotEqual.add(nv.asNode());
                                    if (compareNodeValues(values2.get(0), nv) == 0)
                                        valueInList = true;
                                }
                                if (!valueInList)
                                    listNotEqual.add(values1.get(0).asNode());
                                if (listNotEqual.size() == 1)
                                    expr = e;
                                else
                                    expr = new E_NotOneOf(variable, new PropFuncArg(listNotEqual).asExprList());
                                break;

                            case "gt":
                                ArrayList<Node> listGreater = new ArrayList<>();
                                NodeValue x1 = null;
                                for (NodeValue nv: values1) {
                                    if (compareNodeValues(nv, values2.get(0)) > 0) {
                                        listGreater.add(nv.asNode());
                                        x1 = nv;
                                    }
                                }
                                if (listGreater.isEmpty())
                                    expr = e;
                                else if (listGreater.size() == 1)
                                    expr = new E_LogicalAnd(e, new E_NotEquals(variable, x1));
                                else
                                    expr = new E_LogicalAnd(e, new E_NotOneOf(variable, new PropFuncArg(listGreater).asExprList()));
                                break;

                            case "ge":
                                ArrayList<Node> listGreaterEqual = new ArrayList<>();
                                NodeValue x2 = null;
                                boolean notEqual1 = false;
                                for (NodeValue nv: values1) {
                                    if (compareNodeValues(nv, values2.get(0)) > 0) {
                                        listGreaterEqual.add(nv.asNode());
                                        x2 = nv;
                                    }
                                    else if (compareNodeValues(nv, values2.get(0)) == 0)
                                        notEqual1 = true;
                                }
                                if (notEqual1)
                                    e = new E_GreaterThan(variable, values2.get(0));
                                if (listGreaterEqual.isEmpty())
                                    expr = e;
                                else if (listGreaterEqual.size() == 1)
                                    expr = new E_LogicalAnd(e, new E_NotEquals(variable, x2));
                                else
                                    expr = new E_LogicalAnd(e, new E_NotOneOf(variable, new PropFuncArg(listGreaterEqual).asExprList()));
                                break;

                            case "lt":
                                ArrayList<Node> listLess = new ArrayList<>();
                                NodeValue x3 = null;
                                for (NodeValue nv: values1) {
                                    if (compareNodeValues(nv, values2.get(0)) < 0) {
                                        listLess.add(nv.asNode());
                                        x3 = nv;
                                    }
                                }
                                if (listLess.isEmpty())
                                    expr = e;
                                else if (listLess.size() == 1)
                                    expr = new E_LogicalAnd(e, new E_NotEquals(variable, x3));
                                else
                                    expr = new E_LogicalAnd(e, new E_NotOneOf(variable, new PropFuncArg(listLess).asExprList()));
                                break;

                            case "le":
                                ArrayList<Node> listLessEqual = new ArrayList<>();
                                NodeValue x4 = null;
                                boolean notEqual2 = false;
                                for (NodeValue nv: values1) {
                                    if (compareNodeValues(nv, values2.get(0)) < 0) {
                                        listLessEqual.add(nv.asNode());
                                        x4 = nv;
                                    }
                                    if (compareNodeValues(nv, values2.get(0)) == 0)
                                        notEqual2 = true;
                                }
                                if (notEqual2)
                                    e = new E_LessThan(variable, values2.get(0));
                                if (listLessEqual.isEmpty())
                                    expr = e;
                                else if (listLessEqual.size() == 1)
                                    expr = new E_LogicalAnd(e, new E_NotEquals(variable, x4));
                                else
                                    expr = new E_LogicalAnd(e, new E_NotOneOf(variable, new PropFuncArg(listLessEqual).asExprList()));
                                break;

                            case "in":
                                ArrayList<Node> listIn = new ArrayList<>();
                                NodeValue x5 = null;
                                for (NodeValue nv2: values2) {
                                    boolean in = true;
                                    for (NodeValue nv1: values1) {
                                        if (compareNodeValues(nv2, nv1) == 0)
                                            in = false;
                                    }
                                    if (in) {
                                        listIn.add(nv2.asNode());
                                        x5 = nv2;
                                    }
                                }
                                if (listIn.isEmpty())
                                    expr = null;
                                else if (listIn.size() == 1)
                                    expr = new E_Equals(variable, x5);
                                else
                                    expr = new E_OneOf(variable, new PropFuncArg(listIn).asExprList());
                                break;

                            case "notin":
                                ArrayList<Node> listNotIn = new ArrayList<>();
                                NodeValue x6 = null;
                                for (NodeValue nv: values1) {
                                    listNotIn.add(nv.asNode());
                                    x6 = nv;
                                }
                                for (NodeValue nv: values2) {
                                    if (!listNotIn.contains(nv.asNode())) {
                                        listNotIn.add(nv.asNode());
                                        x6 = nv;
                                    }
                                }
                                if (listNotIn.size() == 1)
                                    expr = new E_NotEquals(variable, x6);
                                else if (listNotIn.size() > 1)
                                    expr = new E_NotOneOf(variable, new PropFuncArg(listNotIn).asExprList());
                                break;

                        }

                        break;

                }

            }

        }

        if (expr == null)
            return null;

        return new ElementFilter(expr);

    }

    /**
     * Compares two node values passed as parameters after checking the relative type (date, double, integer, string)
     *
     * @param x the first value to compare
     * @param y the second value to compare
     * @return 0 if the values are equal
     *         a negative integer if the first value is smaller than the second
     *         a positive integer if the first value is greater than the second
     */
    private int compareNodeValues(NodeValue x, NodeValue y) {

        if (x.isDate()) {

            try {
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
                Date d1 = simpleDateFormat.parse(x.toString().replace("\"", "").replace("^^<http://www.w3.org/2001/XMLSchema#date>", ""));
                Date d2 = simpleDateFormat.parse(y.toString().replace("\"", "").replace("^^<http://www.w3.org/2001/XMLSchema#date>", ""));
                return d1.compareTo(d2);
            }
            catch (java.text.ParseException e) {
                System.out.println(e);
            }

        }

        else if (x.isDouble())
            return Double.compare(Double.parseDouble(x.toString()), Double.parseDouble(y.toString()));

        else if (x.isInteger())
            return Integer.compare(Integer.parseInt(x.toString()), Integer.parseInt(y.toString()));

        else if (x.isString())
            return x.toString().compareTo(y.toString());

        return 0;

    }


}
