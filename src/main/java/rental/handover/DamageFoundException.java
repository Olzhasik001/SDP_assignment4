package rental.handover;

/** Realistic C failure: pre-rental inspection finds damage. */
public class DamageFoundException extends RuntimeException {
    public DamageFoundException(String message) { super(message); }
}
