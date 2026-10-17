package rental.handover;

import java.util.Optional;

/** Entry point #1 of subsystem C: the pre-rental inspection. */
public class DamageInspector {
    private final HandoverRepository repo;
    private final DamageRules rules;

    public DamageInspector(HandoverRepository repo, DamageRules rules) { this.repo = repo; this.rules = rules; }

    /** Persists the inspection and decides PASSED or FAILED (throws DamageFoundException on FAILED). */
    public Inspection inspect(long orderRef, long carId, int damageScore) {
        Inspection ins = repo.insertInspection(orderRef, carId, damageScore);
        boolean ok = rules.isAcceptable(damageScore);
        ins.moveTo(ok ? Inspection.Status.PASSED : Inspection.Status.FAILED);
        repo.updateInspection(ins);
        if (!ok) throw new DamageFoundException("Car " + carId + " has damage score " + damageScore + " - rental refused");
        return ins;
    }

    /** C's own compensation: a PASSED inspection of an aborted rental becomes VOIDED. Idempotent. */
    public void voidByOrder(long orderRef) {
        repo.findInspection(orderRef).ifPresent(i -> {
            if (i.getStatus() == Inspection.Status.PASSED) { i.moveTo(Inspection.Status.VOIDED); repo.updateInspection(i); }
        });
    }

    public Optional<Inspection> inspectionOf(long orderRef) { return repo.findInspection(orderRef); }
}
