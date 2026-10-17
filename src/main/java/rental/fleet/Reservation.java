package rental.fleet;

import java.time.LocalDate;

/** Entity of subsystem A. Enforces its own legal status transitions. */
public class Reservation {

    public enum Status {
        HELD, CONFIRMED, RELEASED;

        public boolean canMoveTo(Status next) {
            return switch (this) {
                case HELD -> next == CONFIRMED || next == RELEASED;
                case CONFIRMED -> next == RELEASED;
                case RELEASED -> false;
            };
        }
    }

    private final long id;
    private final long orderRef;
    private final long carId;
    private final LocalDate from;
    private final LocalDate to;
    private final double price;
    private Status status;

    public Reservation(long id, long orderRef, long carId, LocalDate from, LocalDate to, double price, Status status) {
        this.id = id; this.orderRef = orderRef; this.carId = carId;
        this.from = from; this.to = to; this.price = price; this.status = status;
    }

    public void moveTo(Status next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Reservation " + id + ": illegal transition " + status + " -> " + next);
        }
        this.status = next;
    }

    public long getId() { return id; }
    public long getOrderRef() { return orderRef; }
    public long getCarId() { return carId; }
    public LocalDate getFrom() { return from; }
    public LocalDate getTo() { return to; }
    public double getPrice() { return price; }
    public Status getStatus() { return status; }

    @Override public String toString() {
        return "Reservation#" + id + " car=" + carId + " " + from + ".." + to + " price=" + price + " [" + status + "]";
    }
}
