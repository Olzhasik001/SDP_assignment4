package rental.app;

import java.nio.file.Path;
import rental.billing.*;
import rental.facade.*;
import rental.fleet.*;
import rental.handover.*;

/** Composition root: wires the three subsystems and both facades onto a data directory. */
public record RentalSystem(RentalFacade rental, RentalAdminFacade admin, FleetAvailabilityTracker fleet,
                           DepositHandler billing, DamageInspector inspector, GpsTracker gps) {

    /** Opening the same directory again = "restarting the application". */
    public static RentalSystem open(Path dataDir) {
        var fleet = new FleetAvailabilityTracker(new ReservationRepository(dataDir), new PricingEngine());
        var billing = new DepositHandler(new BillingRepository(dataDir), new InsuranceCalculator());
        var handoverRepo = new HandoverRepository(dataDir);
        var inspector = new DamageInspector(handoverRepo, new DamageRules());
        var gps = new GpsTracker(handoverRepo);
        var orders = new RentalOrderRepository(dataDir);
        return new RentalSystem(new RentalFacade(fleet, billing, inspector, gps, orders),
                new RentalAdminFacade(fleet, billing, inspector, gps, orders), fleet, billing, inspector, gps);
    }
}
