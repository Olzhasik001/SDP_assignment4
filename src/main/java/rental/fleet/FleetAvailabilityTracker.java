package rental.fleet;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Public entry point of subsystem A. Knows nothing about billing or handover. */
public class FleetAvailabilityTracker {
    private final ReservationRepository repo;
    private final PricingEngine pricing;

    public FleetAvailabilityTracker(ReservationRepository repo, PricingEngine pricing) {
        this.repo = repo; this.pricing = pricing;
    }

    /** Step A: find a free car, price it, persist a HELD reservation. */
    public Reservation reserve(long orderRef, String carType, LocalDate from, LocalDate to) {
        Car car = repo.findFreeCar(carType, from, to).orElseThrow(() ->
                new NoCarAvailableException("No free '" + carType + "' car for " + from + ".." + to));
        return repo.insert(orderRef, car.id(), from, to, pricing.price(car.dailyRate(), from, to));
    }

    public void confirm(long orderRef) {
        Reservation r = repo.findByOrder(orderRef).orElseThrow(() ->
                new IllegalStateException("No reservation for order " + orderRef));
        r.moveTo(Reservation.Status.CONFIRMED);
        repo.updateStatus(r);
    }

    /** Compensation. Idempotent: safe to call twice or when nothing was reserved. */
    public void releaseByOrder(long orderRef) {
        repo.findByOrder(orderRef).ifPresent(r -> {
            if (r.getStatus() != Reservation.Status.RELEASED) {
                r.moveTo(Reservation.Status.RELEASED);
                repo.updateStatus(r);
            }
        });
    }

    public Optional<Reservation> reservationOf(long orderRef) { return repo.findByOrder(orderRef); }
    public List<Car> cars() { return repo.allCars(); }
}
