package rental.fleet;

/** Thrown at the very first step: nothing has been committed yet. */
public class NoCarAvailableException extends RuntimeException {
    public NoCarAvailableException(String message) { super(message); }
}
