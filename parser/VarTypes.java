package parser;

import org.apache.jena.graph.Node;
import org.apache.jena.sparql.core.Var;

import java.util.ArrayList;

/**
 * A class representing a variable contained in a SPARQL query related to a subject or object node and his possible rdf:types
 */
public class VarTypes {

    private final Var variable;
    private final ArrayList<Node> types;

    /**
     * @param variable a Var object representing a subject or object variable contained in the SPARQL query
     * @param types a list of Node object representing the possible rdf:types of the variable
     */
    public VarTypes(Var variable, ArrayList<Node> types) {

        this.variable = variable;
        this.types = types;

    }

    @Override
    public String toString() {
        return "Variable: " + variable + ", " + "Types: " + types;
    }

    /**
     * @return A "Var" object representing the variable
     */
    public Var getVar() {
        return variable;
    }

    /**
     * @return A list of Node object representing the possible rdf:types of the variable
     */
    public ArrayList<Node> getTypes() {
        return types;
    }

}
