package rental.handover;

import java.util.Optional;

/** Entry point #2 of subsystem C: GPS tracker pool. */
public class GpsTracker {
    private final HandoverRepository repo;

    public GpsTracker(HandoverRepository repo) { this.repo = repo; }

    public String activate(long orderRef) {
        return repo.assignFreeDevice(orderRef).orElseThrow(() ->
                new GpsUnavailableException("No free GPS tracker left"));
    }

    /** Compensation. Idempotent. */
    public void releaseByOrder(long orderRef) { repo.freeDevice(orderRef); }

    public Optional<String> deviceOf(long orderRef) { return repo.findDevice(orderRef); }
}
