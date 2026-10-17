package rental.facade;

import rental.billing.DepositHandler;
import rental.fleet.FleetAvailabilityTracker;
import rental.fleet.NoCarAvailableException;
import rental.fleet.PricingEngine;
import rental.fleet.Reservation;
import rental.handover.DamageInspector;
import rental.handover.GpsTracker;
import rental.facade.RentalOrder.ChainStatus;

/**
 * Facade #1 - the client workflow: A (reserve) -> B (insure + deposit) -> C (inspect + GPS).
 * Progress is written to disk BEFORE each step, so a restart can see exactly how far we got.
 */
public class RentalFacade {
    private final FleetAvailabilityTracker fleet;
    private final DepositHandler billing;
    private final DamageInspector inspector;
    private final GpsTracker gps;
    private final RentalOrderRepository orders;

    public RentalFacade(FleetAvailabilityTracker fleet, DepositHandler billing, DamageInspector inspector,
                        GpsTracker gps, RentalOrderRepository orders) {
        this.fleet = fleet; this.billing = billing; this.inspector = inspector; this.gps = gps; this.orders = orders;
    }

    public RentalOrder rentCar(RentalRequest req) { return run(req, false); }

    /** DEMO / TEST ONLY: runs A and B, then "crashes" (returns) before C, leaving the order PENDING_C on disk. */
    public RentalOrder rentCarAndCrashBeforeC(RentalRequest req) { return run(req, true); }

    private RentalOrder run(RentalRequest req, boolean crashBeforeC) {
        validate(req);                                            // failure case 3: nothing committed
        RentalOrder order = orders.create(req);
        long id = order.getId();

        // ---- Step A ----
        Reservation reservation;
        try {
            reservation = fleet.reserve(id, req.carType(), req.from(), req.to());
        } catch (NoCarAvailableException e) {                     // failure case: A fails, nothing to undo
            orders.update(id, ChainStatus.FAILED, "A failed: " + e.getMessage());
            throw e;
        }
        orders.update(id, ChainStatus.PENDING_B, "A done: " + reservation);

        // ---- Step B ----
        long days = PricingEngine.days(req.from(), req.to());
        try {
            billing.authorize(id, reservation.getPrice(), days, req.driverAge(), req.coverage(), req.cardBalance());
        } catch (RuntimeException e) {                            // depth 1: B failed, A committed -> undo A only
            fleet.releaseByOrder(id);
            orders.update(id, ChainStatus.COMPENSATED, "B failed, A undone: " + e.getMessage());
            throw e;
        }
        orders.update(id, ChainStatus.PENDING_C, "B done");
        if (crashBeforeC) return orders.findById(id).orElseThrow();

        // ---- Step C ----
        try {
            inspector.inspect(id, reservation.getCarId(), req.inspectionDamageScore());
            gps.activate(id);
        } catch (RuntimeException e) {                            // depth 2: C failed, A+B committed -> undo C leftovers, B, then A
            inspector.voidByOrder(id);
            gps.releaseByOrder(id);
            billing.refundByOrder(id);
            fleet.releaseByOrder(id);
            orders.update(id, ChainStatus.COMPENSATED, "C failed, B and A undone: " + e.getMessage());
            throw e;
        }

        // ---- Finalize ----
        fleet.confirm(id);
        billing.activate(id);
        orders.update(id, ChainStatus.COMPLETED, "Rental confirmed");
        return orders.findById(id).orElseThrow();
    }

    private void validate(RentalRequest r) {
        if (r == null) throw new InvalidRentalRequestException("Request is missing");
        if (r.customer() == null || r.customer().isBlank()) throw new InvalidRentalRequestException("Customer name is required");
        if (r.carType() == null || r.carType().isBlank()) throw new InvalidRentalRequestException("Car type is required");
        if (r.from() == null || r.to() == null || !r.to().isAfter(r.from()))
            throw new InvalidRentalRequestException("Return date must be after pick-up date");
        if (r.driverAge() < 18) throw new InvalidRentalRequestException("Driver must be at least 18");
        if (r.coverage() == null) throw new InvalidRentalRequestException("Coverage is required");
        if (r.cardBalance() < 0) throw new InvalidRentalRequestException("Card balance cannot be negative");
        if (r.inspectionDamageScore() < 0 || r.inspectionDamageScore() > 100)
            throw new InvalidRentalRequestException("Damage score must be 0..100");
    }
}
