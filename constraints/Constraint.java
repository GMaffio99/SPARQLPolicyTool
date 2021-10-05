package constraints;

/**
 * Abstract class that represent a constraint
 */
public abstract class Constraint {

    private final String user;


    public Constraint(String user) {
        this.user = user;
    }

    /**
     * @return A string representing the user/category of users to which the constraint refers
     */
    public String getUser() {
        return this.user;
    }

}
