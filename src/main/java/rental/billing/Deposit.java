package rental.billing;

/** Entity of subsystem B with its own legal transitions. */
public class Deposit {

    public enum Status {
        HELD, CAPTURED, REFUNDED;

        public boolean canMoveTo(Status next) {
            return this == HELD && (next == CAPTURED || next == REFUNDED);
        }
    }

    private final long id;
    private final long orderRef;
    private final double amount;
    private Status status;

    public Deposit(long id, long orderRef, double amount, Status status) {
        this.id = id; this.orderRef = orderRef; this.amount = amount; this.status = status;
    }

    public void moveTo(Status next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Deposit " + id + ": illegal transition " + status + " -> " + next);
        }
        this.status = next;
    }

    public long getId() { return id; }
    public long getOrderRef() { return orderRef; }
    public double getAmount() { return amount; }
    public Status getStatus() { return status; }

    @Override public String toString() { return "Deposit#" + id + " amount=" + amount + " [" + status + "]"; }
}
