package rental.billing;

/** Second entity of subsystem B (QUOTED -> ACTIVE -> CANCELLED). */
public class InsurancePolicy {

    public enum Coverage { BASIC, FULL }

    public enum Status {
        QUOTED, ACTIVE, CANCELLED;

        public boolean canMoveTo(Status next) {
            return switch (this) {
                case QUOTED -> next == ACTIVE || next == CANCELLED;
                case ACTIVE -> next == CANCELLED;
                case CANCELLED -> false;
            };
        }
    }

    private final long id;
    private final long orderRef;
    private final Coverage coverage;
    private final double premium;
    private Status status;

    public InsurancePolicy(long id, long orderRef, Coverage coverage, double premium, Status status) {
        this.id = id; this.orderRef = orderRef; this.coverage = coverage; this.premium = premium; this.status = status;
    }

    public void moveTo(Status next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Policy " + id + ": illegal transition " + status + " -> " + next);
        }
        this.status = next;
    }

    public long getId() { return id; }
    public long getOrderRef() { return orderRef; }
    public Coverage getCoverage() { return coverage; }
    public double getPremium() { return premium; }
    public Status getStatus() { return status; }

    @Override public String toString() {
        return "Policy#" + id + " " + coverage + " premium=" + premium + " [" + status + "]";
    }
}
