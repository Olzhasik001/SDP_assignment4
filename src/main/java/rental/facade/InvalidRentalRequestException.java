package rental.facade;

/** Extra failure case (3.6): invalid input, detected before anything is committed. */
public class InvalidRentalRequestException extends RuntimeException {
    public InvalidRentalRequestException(String message) { super(message); }
}
