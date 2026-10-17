package rental.facade;

import java.time.LocalDate;

/** Persisted progress of the A -> B -> C chain. */
public class RentalOrder {

    public enum ChainStatus { STARTED, PENDING_B, PENDING_C, COMPLETED, COMPENSATED, FAILED }

    private final long id;
    private final String customer;
    private final String carType;
    private final LocalDate from;
    private final LocalDate to;
    private ChainStatus status;
    private String note;

    public RentalOrder(long id, String customer, String carType, LocalDate from, LocalDate to, ChainStatus status, String note) {
        this.id = id; this.customer = customer; this.carType = carType; this.from = from; this.to = to;
        this.status = status; this.note = note;
    }

    public long getId() { return id; }
    public String getCustomer() { return customer; }
    public String getCarType() { return carType; }
    public LocalDate getFrom() { return from; }
    public LocalDate getTo() { return to; }
    public ChainStatus getStatus() { return status; }
    public String getNote() { return note; }

    @Override public String toString() {
        return "Order#" + id + " " + customer + " " + carType + " " + from + ".." + to + " [" + status + "] " + (note == null ? "" : note);
    }
}
