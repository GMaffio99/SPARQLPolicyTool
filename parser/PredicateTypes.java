package parser;

import org.apache.jena.graph.Node;
import org.apache.jena.sparql.core.Var;

import java.util.ArrayList;

/**
 * A class representing a variable contained in a SPARQL query related to a predicate and his possible URI values
 */
public class PredicateTypes {

    private final Var predicate;
    private final ArrayList<Node> types;

    /**
     * @param predicate a Var object representing a predicate variable contained in the SPARQL query
     * @param types a list of Node objects representing the possible URI values of the predicate
     */
    public PredicateTypes(Var predicate, ArrayList<Node> types) {

        this.predicate = predicate;
        this.types = types;

    }

    @Override
    public String toString() {
        return "Predicate: " + predicate + ", " + "Types: " + types;
    }

    /**
     * @return A Var object representing the predicate variable
     */
    public Var getPredicate() {
        return predicate;
    }

    /**
     * @return A list of Node objects representing the possible URI values of the predicate
     */
    public ArrayList<Node> getTypes() {
        return types;
    }

}
