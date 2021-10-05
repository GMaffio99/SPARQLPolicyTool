package parser;

import constraints.AttributeConstraint;
import constraints.NodeConstraint;
import constraints.PredicateConstraint;
import org.apache.jena.graph.Node;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

/**
 * A class representing a list of constraints read from an external file.
 *
 * The object is created when a SPARQLParser is instantiated.
 * It contains three different lists, one for each type of constraint
 */
public class ConstraintsList {

    private final ArrayList<NodeConstraint> nodeConstraints;
    private final ArrayList<PredicateConstraint> predicateConstraints;
    private final ArrayList<AttributeConstraint> attributeConstraints;

    public ConstraintsList() {

        nodeConstraints = new ArrayList<>();
        predicateConstraints = new ArrayList<>();
        attributeConstraints = new ArrayList<>();

    }

    /**
     * Read an external file containing the constraints and pull them in the respective list
     *
     * @param file the string representing the file to read
     */
    public void readConstraintsFile(String file) {

        try {

            JSONParser parser = new JSONParser();
            JSONArray constraints = (JSONArray) parser.parse(new FileReader(file));

            for (Object o : constraints) {

                JSONObject constraint = (JSONObject) o;

                switch ((String) constraint.get("constraint")) {

                    case "node":
                        nodeConstraints.add(new NodeConstraint(constraint));
                        break;
                    case "predicate":
                        predicateConstraints.add(new PredicateConstraint(constraint));
                        break;
                    case "attribute":
                        attributeConstraints.add(new AttributeConstraint(constraint));
                        break;
                    default:
                        break;

                }

            }

        }

        catch (IOException | ParseException e) {
            System.out.println(e);
        }

    }


    /**
     * Return a list of "NodeConstraint" objects filtered by user and type of nodes
     *
     * @param user the user whose contraints are to be returned
     * @param type the rdf:type of nodes whose constraints are to be returned
     * @return a list of "NodeConstraint" objects
     */
    public ArrayList<NodeConstraint> getNodeConstraints(String user, Node type) {

        ArrayList<NodeConstraint> result = new ArrayList<>();

        for (NodeConstraint c: nodeConstraints) {

            if (c.getUser().equals(user) && c.getNodeType().equals(type)) {

                if (!c.hasNodes()) {
                    result.clear();
                    result.add(c);
                    return result;
                }

                else
                    result.add(c);

            }

        }

        return result;

    }

    /**
     * Return a list of PredicateConstraint objects filtered by user, type of subject nodes, predicate URI and type of object nodes
     *
     * @param user the user whose constraints are to be returned
     * @param subjectType the rdf:type of subject nodes
     * @param predicate the predicate URI
     * @param objectType the rdf:type of object nodes
     * @return a list of "PredicateConstraint" objects
     */
    public ArrayList<PredicateConstraint> getPredicateConstraints(String user, String subjectType, String predicate, String objectType) {

        ArrayList<PredicateConstraint> result = new ArrayList<>();

        for (PredicateConstraint c: predicateConstraints) {

            if (c.getUser().equals(user) && c.getSubjectType().equals(subjectType) && c.getPredicate().equals(predicate) && c.getObjectType().equals(objectType)) {

                if (!c.hasSubjects() && !c.hasObjects()) {
                    result.clear();
                    result.add(c);
                    return result;
                }

                else
                    result.add(c);

            }

        }

        return result;

    }

    /**
     * Return a list of "AttributeConstraint" objects filtered by user, type of subject nodes and predicate URI
     *
     * @param user the user whose constraints are to be returned
     * @param subjectType the rdf:type of subject nodes
     * @param predicate the predicate URI
     * @return a list of "AttributeConstraint" objects
     */
    public ArrayList<AttributeConstraint> getAttributeConstraints(String user, String subjectType, String predicate) {

        ArrayList<AttributeConstraint> result = new ArrayList<>();

        boolean hasSubjects = false;

        for (AttributeConstraint c: attributeConstraints) {

            if (c.getUser().equals(user) && c.getSubjectType().equals(subjectType) && c.getPredicate().equals(predicate)) {

                if (c.getSymbol().equals("X")) {

                    if (c.hasSubjects()) {

                        if (!hasSubjects) {
                            result.clear();
                            hasSubjects = true;
                        }

                        result.add(c);

                    }

                    else {
                        result.clear();
                        result.add(c);
                        return result;
                    }

                }

                else if (!hasSubjects)
                    result.add(c);

            }
        }

        return result;

    }

}
