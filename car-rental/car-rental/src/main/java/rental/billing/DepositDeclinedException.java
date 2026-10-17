package rental.billing;

/** Realistic B failure: the customer's card cannot cover deposit + insurance. */
public class DepositDeclinedException extends RuntimeException {
    public DepositDeclinedException(String message) { super(message); }
}
