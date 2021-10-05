package parser;

import org.apache.jena.graph.Node;

import java.util.ArrayList;

/**
 * A class representing a URI contained in a SPARQL query related to a subject or object node and his possible rdf:types
 */
public class UriTypes {

    private final Node uri;
    private final ArrayList<Node> types;

    /**
     * @param uri a Node object representing a subject or object URI contained in the SPARQL query
     * @param types a list of Node objects representing the possible rdf:types of the URI
     */
    public UriTypes(Node uri, ArrayList<Node> types) {

        this.uri = uri;
        this.types = types;

    }

    @Override
    public String toString() {
        return "URI: " + uri + ", " + "Types: " + types;
    }

    /**
     * @return A Node object representing the URI
     */
    public Node getUri() {
        return uri;
    }

    /**
     * @return A list of Node objects representing the possible rdf:types of the URI
     */
    public ArrayList<Node> getTypes() {
        return types;
    }

}
