package rental.facade;

import java.util.ArrayList;
import java.util.List;
import rental.fleet.Car;
import rental.billing.DepositHandler;
import rental.facade.RentalOrder.ChainStatus;
import rental.fleet.FleetAvailabilityTracker;
import rental.handover.DamageInspector;
import rental.handover.GpsTracker;

/** Facade #2 - narrow admin workflow: inspection, manual compensation, restart recovery. */
public class RentalAdminFacade {
    private final FleetAvailabilityTracker fleet;
    private final DepositHandler billing;
    private final DamageInspector inspector;
    private final GpsTracker gps;
    private final RentalOrderRepository orders;

    public RentalAdminFacade(FleetAvailabilityTracker fleet, DepositHandler billing, DamageInspector inspector,
                             GpsTracker gps, RentalOrderRepository orders) {
        this.fleet = fleet; this.billing = billing; this.inspector = inspector; this.gps = gps; this.orders = orders;
    }

    public List<RentalOrder> listOrders() { return orders.all(); }

    /** Full picture of one order across all three subsystems, read from disk. */
    public String inspect(long orderId) {
        RentalOrder o = orders.findById(orderId).orElseThrow(() -> new IllegalArgumentException("No such order " + orderId));
        return o + "\n  A: " + fleet.reservationOf(orderId).map(Object::toString).orElse("-")
                + "\n  B: " + billing.depositOf(orderId).map(Object::toString).orElse("-")
                + " / " + billing.policyOf(orderId).map(Object::toString).orElse("-")
                + "\n  C: " + inspector.inspectionOf(orderId).map(Object::toString).orElse("-")
                + " / GPS " + gps.deviceOf(orderId).orElse("-");
    }

    /** Manually cancels an order: undoes C leftovers, B, then A (all steps idempotent). */
    public RentalOrder manualCompensate(long orderId) {
        RentalOrder o = orders.findById(orderId).orElseThrow(() -> new IllegalArgumentException("No such order " + orderId));
        if (o.getStatus() == ChainStatus.COMPENSATED || o.getStatus() == ChainStatus.FAILED) {
            throw new IllegalStateException("Order " + orderId + " is already " + o.getStatus());
        }
        undoCBA(orderId);
        orders.update(orderId, ChainStatus.COMPENSATED, "Manually compensated by admin");
        return orders.findById(orderId).orElseThrow();
    }

    /**
     * Restart recovery: looks ONLY at what is on disk.
     *  STARTED   -> A may or may not have committed: release A, mark COMPENSATED
     *  PENDING_B -> A committed, B unknown: undo B leftovers + A
     *  PENDING_C -> A and B committed: undo C leftovers, B, then A
     */
    public List<RentalOrder> recoverPending() {
        List<RentalOrder> recovered = new ArrayList<>();
        for (RentalOrder o : orders.findByStatus(ChainStatus.STARTED)) {
            fleet.releaseByOrder(o.getId());
            orders.update(o.getId(), ChainStatus.COMPENSATED, "Recovered after restart (was STARTED)");
            recovered.add(orders.findById(o.getId()).orElseThrow());
        }
        for (RentalOrder o : orders.findByStatus(ChainStatus.PENDING_B)) {
            billing.refundByOrder(o.getId());
            fleet.releaseByOrder(o.getId());
            orders.update(o.getId(), ChainStatus.COMPENSATED, "Recovered after restart (was PENDING_B)");
            recovered.add(orders.findById(o.getId()).orElseThrow());
        }
        for (RentalOrder o : orders.findByStatus(ChainStatus.PENDING_C)) {
            undoCBA(o.getId());
            orders.update(o.getId(), ChainStatus.COMPENSATED, "Recovered after restart (was PENDING_C)");
            recovered.add(orders.findById(o.getId()).orElseThrow());
        }
        return recovered;
    }
    public List<Car> fleet() { return fleet.cars(); }
    private void undoCBA(long id) {
        inspector.voidByOrder(id);
        gps.releaseByOrder(id);
        billing.refundByOrder(id);
        fleet.releaseByOrder(id);
    }
}
