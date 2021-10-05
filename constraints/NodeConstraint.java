package constraints;

import org.apache.jena.graph.Node;
import org.apache.jena.sparql.util.NodeUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;

/**
 * A class representing a constraint on nodes
 * Extends the abstract class "Constraint"
 *
 * The constraint can be referred to a class of nodes obtained by the rdf:type predicate
 * or to specific nodes belonging to a certain class of nodes
 */
public class NodeConstraint extends Constraint {

    private final String nodeType;
    private final ArrayList<String> nodes;

    /**
     * Creates a "NodeConstraint" by passing a JSON object as parameter
     *
     * @param constraint JSON object containing the constraint information
     */
    public NodeConstraint(JSONObject constraint) {

        super((String) constraint.get("user"));
        nodeType = (String) constraint.get("node-type");

        if (constraint.containsKey("nodes")) {
            nodes = new ArrayList<>();
            for (Object o: (JSONArray) constraint.get("nodes"))
                nodes.add((String) o);
        }
        else
            this.nodes = null;

    }

    /**
     * @return A Node object representing the rdf:type of the nodes to which the constraint refers
     */
    public Node getNodeType() {
        return NodeUtils.asNode(nodeType);
    }

    /**
     * @return A list of Nodes representing the URIS of the nodes to which the constraint refers
     */
    public ArrayList<Node> getNodes() {

        ArrayList<Node> result = new ArrayList<>();

        for (String n: nodes)
            result.add(NodeUtils.asNode(n));

        return result;

    }

    /**
     * @return A boolean value representing if the constraint refers to specific nodes contained in the "nodes" field
     */
    public boolean hasNodes() {
        return nodes != null;
    }

}
