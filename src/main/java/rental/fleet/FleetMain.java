package rental.fleet;

import java.nio.file.Path;
import java.time.LocalDate;

/** Subsystem A used completely on its own (no facade, no B, no C). */
public class FleetMain {
    public static void main(String[] args) {
        var fleet = new FleetAvailabilityTracker(new ReservationRepository(Path.of("data-standalone")), new PricingEngine());
        var r = fleet.reserve(999, "Economy", LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 4));
        System.out.println("Standalone A: " + r);
        fleet.releaseByOrder(999);
        System.out.println("After release: " + fleet.reservationOf(999).orElseThrow());
    }
}
