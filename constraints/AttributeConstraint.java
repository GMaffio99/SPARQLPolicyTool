package constraints;

import org.apache.jena.graph.Node;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.util.NodeUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * A class representing a constraint on object attributes reached by specific subject class and predicate
 * Extends the abstract class "Constraint"
 *
 * The constraint can be referred to classes of subjects or specific subject nodes.
 * The types of values managed are string, integer, double and date.
 * The constraint can prohibit the access to the attribute or to some values of the attribute
 * The constraint operators managed are =, !=, >, >=, <, <=, between, in, not in
 */
public class AttributeConstraint extends Constraint {

    private final String subjectType;
    private final ArrayList<String> subjects;
    private final String predicate;
    private final String objectType;
    private final String symbol;
    private final ArrayList<String> values;

    /**
     * Creates an "AttributeConstraint" by passing a JSON object as a parameter
     *
     * @param constraint JSON object containing the constraint information
     */
    public AttributeConstraint(JSONObject constraint) {

        super((String) constraint.get("user"));
        subjectType = (String) constraint.get("subject-type");
        predicate = (String) constraint.get("predicate");
        symbol = (String) constraint.get("symbol");

        if (constraint.containsKey("object-type"))
            objectType = (String) constraint.get("object-type");
        else
            objectType = null;

        if (constraint.containsKey("subjects")) {
            subjects = new ArrayList<>();
            for (Object o: (JSONArray) constraint.get("subjects"))
                subjects.add((String) o);
        }
        else
            subjects = null;

        if (constraint.containsKey("values")) {
            values = new ArrayList<>();
            for (Object o: (JSONArray) constraint.get("values"))
                values.add((String) o);
        }
        else
            values = null;

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
     * @return A string representing the constraint operator applied to the attribute
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * @return A list of Node objects representing the specific subject nodes to which the constraint refers
     */
    public ArrayList<Node> getSubjects() {

        ArrayList<Node> result = new ArrayList<>();

        for (String s: subjects)
            result.add(NodeUtils.asNode(s));

        return result;

    }

    /**
     * @return A list of strings representing the value/values that combined to the operator gives the range of accessible values
     */
    public ArrayList<String> getValues() {
        return values;
    }

    /**
     * @return A list of Expr objects representing the value/values that combined to the operator gives the range of accessible values
     */
    public ArrayList<Expr> getExprValues() {

        ArrayList<Expr> result = new ArrayList<>();

        for (String s: values) {

            NodeValue value;

            switch (objectType) {

                case "date":
                    value = NodeValue.makeDate(s);
                    break;

                case "double":
                    value = NodeValue.makeDouble(Double.parseDouble(s));
                    break;

                case "integer":
                    value = NodeValue.makeInteger(s);
                    break;

                case "string":
                    value = NodeValue.makeString(s);
                    break;

                default:
                    value = NodeValue.makeNode(NodeUtils.asNode(s));
                    break;

            }

            result.add((Expr) value);

        }

        return result;

    }

    /**
     * @return A list of Node objects representing the value/values that combined to the operator gives the range of accessible values
     */
    public ArrayList<Node> getNodeValues() {

        ArrayList<Node> result = new ArrayList<>();

        for (String s: values)
            result.add(NodeUtils.asNode(s));

        return result;

    }

    /**
     * @return A boolean value representing if the constraint refers to specific subject nodes
     */
    public boolean hasSubjects() {
        return subjects != null;
    }

    /**
     * Method called to compare the first (and only) value of the "values" field to a value passed as parameter
     * (in the case the operator is one of =, !=, >, >=, <, <=
     *
     * @param s the value to compare with the value of the constraint
     * @return 0 if the values are equal
     *         a negative integer if the value passed as parameter is greater
     *         a positive integer if the value passed as parameter is smaller
     */
    public int compare(String s) {

        switch (objectType) {

            case "date":
                try {
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
                    Date d1 = simpleDateFormat.parse(values.get(0));
                    Date d2 = simpleDateFormat.parse(s);
                    return d1.compareTo(d2);
                }
                catch (java.text.ParseException e) {
                    System.out.println(e);
                }

            case "double":  return Double.compare(Double.parseDouble(values.get(0)), Double.parseDouble(s));
            case "integer": return Integer.compare(Integer.parseInt(values.get(0)), Integer.parseInt(s));
            case "string":  return values.get(0).compareTo(s);
            default:        return 0;

        }

    }

    /**
     * Method called to verify if a value passed as parameter is between the two values contained in the "values" field
     * (in the case the operator is between)
     *
     * @param s the value to compare with the values of the constraint
     * @return true if the value is between, false if not
     */
    public boolean between(String s) {

        switch (objectType) {

            case "date":
                try {
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
                    Date d1 = simpleDateFormat.parse(values.get(0));
                    Date d2 = simpleDateFormat.parse(values.get(1));
                    Date d3 = simpleDateFormat.parse(s);
                    return ( d1.before(d3) || d1.equals(d3) ) && ( d2.after(d3) || d2.equals(d3) );
                }
                catch (java.text.ParseException e) {
                    System.out.println(e);
                }

            case "double":
                double d1 = Double.parseDouble(values.get(0));
                double d2 = Double.parseDouble(values.get(1));
                double d3 = Double.parseDouble(s);
                return d1 <= d3 && d2 >= d3;

            case "integer":
                int i1 = Integer.parseInt(values.get(0));
                int i2 = Integer.parseInt(values.get(1));
                int i3 = Integer.parseInt(s);
                return i1 <= i3 && i2 >= i3;

            case "string":
                return values.get(0).compareTo(s) <= 0 && values.get(1).compareTo(s) >= 0;

            default:
                return false;

        }

    }

    /**
     * Method called to verify if the "values" field contains the value passed as a parameter
     * (in the case the operator is in or not in)
     *
     * @param s the value to compare with the values of the constraint
     * @return true if the value is contained in the list, false if not
     */
    public boolean contains(String s) {
        return values.contains(s);
    }

}
