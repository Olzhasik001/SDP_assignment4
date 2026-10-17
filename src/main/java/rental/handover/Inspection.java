package rental.handover;

/** Entity of subsystem C with its own legal transitions. */
public class Inspection {

    public enum Status {
        PENDING, PASSED, FAILED, VOIDED;

        public boolean canMoveTo(Status next) {
            return switch (this) {
                case PENDING -> next == PASSED || next == FAILED;
                case PASSED -> next == VOIDED;
                case FAILED, VOIDED -> false;
            };
        }
    }

    private final long id;
    private final long orderRef;
    private final long carId;
    private final int damageScore;
    private Status status;

    public Inspection(long id, long orderRef, long carId, int damageScore, Status status) {
        this.id = id; this.orderRef = orderRef; this.carId = carId; this.damageScore = damageScore; this.status = status;
    }

    public void moveTo(Status next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Inspection " + id + ": illegal transition " + status + " -> " + next);
        }
        this.status = next;
    }

    public long getId() { return id; }
    public long getOrderRef() { return orderRef; }
    public long getCarId() { return carId; }
    public int getDamageScore() { return damageScore; }
    public Status getStatus() { return status; }

    @Override public String toString() {
        return "Inspection#" + id + " car=" + carId + " damageScore=" + damageScore + " [" + status + "]";
    }
}
