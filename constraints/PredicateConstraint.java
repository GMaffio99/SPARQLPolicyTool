package constraints;

import org.apache.jena.graph.Node;
import org.apache.jena.sparql.util.NodeUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;

/**
 * A class representing a constraint on object nodes of a specific predicate
 * Extends the abstract class "Constraint"
 *
 * The constraint can be referred to classes of subjects and objects,
 * to specific subject nodes or to specific object nodes
 */
public class PredicateConstraint extends Constraint {

    private final String subjectType;
    private final ArrayList<String> subjects;
    private final String predicate;
    private final String objectType;
    private final ArrayList<String> objects;

    /**
     * Creates a "PredicateConstraint" by passing a JSON object as a parameter
     *
     * @param constraint JSON object containing the constraint information
     */
    public PredicateConstraint(JSONObject constraint) {

        super((String) constraint.get("user"));
        subjectType = (String) constraint.get("subject-type");
        predicate = (String) constraint.get("predicate");
        objectType = (String) constraint.get("object-type");

        if (constraint.containsKey("subjects")) {
            subjects = new ArrayList<>();
            for (Object o: (JSONArray) constraint.get("subjects"))
                subjects.add((String) o);
        }
        else
            subjects = null;

        if (constraint.containsKey("objects")) {
            objects = new ArrayList<>();
            for (Object o: (JSONArray) constraint.get("objects"))
                objects.add((String) o);
        }
        else
            objects = null;

    }

    /**
     * @return A string representing the rdf:type of the subject nodes
     */
    public String getSubjectType() {
        return subjectType;
    }

    /**
     * @return A string representing the predicate URI
     */
    public String getPredicate() {
        return predicate;
    }

    /**
     * @return A string representing the rdf:type of the object nodes
     */
    public String getObjectType() {
        return objectType;
    }

    /**
     * @return A list of Node objects representing the specific subjects nodes to which the constraint refers
     */
    public ArrayList<Node> getSubjects() {

        ArrayList<Node> result = new ArrayList<>();

        for (String s: subjects)
            result.add(NodeUtils.asNode(s));

        return result;

    }

    /**
     * @return A list of Node objects representing the specific object nodes to which the constraint refers
     */
    public ArrayList<Node> getObjects() {

        ArrayList<Node> result = new ArrayList<>();

        for (String s: objects)
            result.add(NodeUtils.asNode(s));

        return result;

    }

    /**
     * @return A boolean value representing if the constraint refers to specific subject nodes contained in the "subjects" filed
     */
    public boolean hasSubjects() {
        return subjects != null;
    }

    /**
     * @return A boolean value representing if the constraint refers to specific object nodes contained in the "objects" field
     */
    public boolean hasObjects() {
        return objects != null;
    }

}
