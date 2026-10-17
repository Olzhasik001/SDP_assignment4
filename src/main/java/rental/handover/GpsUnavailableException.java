package rental.handover;

/** Realistic C failure: every GPS tracker is already in use. */
public class GpsUnavailableException extends RuntimeException {
    public GpsUnavailableException(String message) { super(message); }
}
