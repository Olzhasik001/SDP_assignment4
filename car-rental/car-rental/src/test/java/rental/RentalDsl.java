package rental;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import rental.app.RentalSystem;
import rental.billing.Deposit;
import rental.billing.InsurancePolicy;
import rental.facade.RentalOrder;
import rental.facade.RentalOrder.ChainStatus;
import rental.facade.RentalRequest;
import rental.fleet.Reservation;
import rental.handover.Inspection;

/**
 * Domain-specific testing language: tests read like
 *   rental.aRental().ofType("SUV").withCardBalance(5).attempt();
 *   rental.thenLatestOrderIs(COMPENSATED).andReservationIs(RELEASED)...
 */
class RentalDsl {
    static final LocalDate PICKUP = LocalDate.of(2030, 1, 10);

    private final Path dir;
    RentalSystem system;

    RentalDsl(Path dir) { this.dir = dir; this.system = RentalSystem.open(dir); }

    /** Simulates stopping and starting the application again on the same files. */
    RentalDsl restartApplication() { system = RentalSystem.open(dir); return this; }

    RequestBuilder aRental() { return new RequestBuilder(); }

    RentalOrder latestOrder() {
        List<RentalOrder> all = system.admin().listOrders();
        return all.get(all.size() - 1);
    }

    // ---------- then... assertions (read from disk, through the subsystems) ----------
    RentalDsl thenOrderIs(long id, ChainStatus expected) {
        assertEquals(expected, system.admin().listOrders().stream().filter(o -> o.getId() == id)
                .findFirst().orElseThrow().getStatus());
        return this;
    }
    RentalDsl thenLatestOrderIs(ChainStatus expected) { assertEquals(expected, latestOrder().getStatus()); return this; }
    RentalDsl andReservationIs(Reservation.Status s) { assertEquals(s, system.fleet().reservationOf(latestOrder().getId()).orElseThrow().getStatus()); return this; }
    RentalDsl andDepositIs(Deposit.Status s) { assertEquals(s, system.billing().depositOf(latestOrder().getId()).orElseThrow().getStatus()); return this; }
    RentalDsl andPolicyIs(InsurancePolicy.Status s) { assertEquals(s, system.billing().policyOf(latestOrder().getId()).orElseThrow().getStatus()); return this; }
    RentalDsl andNoDepositExists() { assertTrue(system.billing().depositOf(latestOrder().getId()).isEmpty()); return this; }
    RentalDsl andNoGpsIsAssigned() { assertTrue(system.gps().deviceOf(latestOrder().getId()).isEmpty()); return this; }
    RentalDsl andGpsIsAssigned() { assertTrue(system.gps().deviceOf(latestOrder().getId()).isPresent()); return this; }
    RentalDsl andInspectionIs(Inspection.Status s) { assertEquals(s, system.inspector().inspectionOf(latestOrder().getId()).orElseThrow().getStatus()); return this; }
    RentalDsl andNoInspectionExists() { assertTrue(system.inspector().inspectionOf(latestOrder().getId()).isEmpty()); return this; }

    class RequestBuilder {
        private String type = "Economy";
        private int days = 3;
        private int age = 30;
        private double balance = 5000;
        private int damage = 5;
        private InsurancePolicy.Coverage coverage = InsurancePolicy.Coverage.BASIC;
        private LocalDate from = PICKUP;

        RequestBuilder ofType(String t) { type = t; return this; }
        RequestBuilder forDays(int d) { days = d; return this; }
        RequestBuilder startingOn(LocalDate d) { from = d; return this; }
        RequestBuilder withCardBalance(double b) { balance = b; return this; }
        RequestBuilder withDamageScore(int d) { damage = d; return this; }
        RequestBuilder driverAged(int a) { age = a; return this; }
        RequestBuilder withFullCoverage() { coverage = InsurancePolicy.Coverage.FULL; return this; }

        RentalRequest build() {
            return new RentalRequest("Test Customer", type, from, from.plusDays(days), age, coverage, balance, damage);
        }
        /** Books and expects success. */
        RentalOrder book() { return system.rental().rentCar(build()); }
        /** Books and expects the given failure; returns the exception for further checks. */
        <T extends RuntimeException> T bookExpectingFailure(Class<T> type) {
            return assertThrows(type, () -> system.rental().rentCar(build()));
        }
    }
}
