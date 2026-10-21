package rental;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rental.billing.*;
import rental.billing.strategy.*;
import rental.fleet.*;
import rental.fleet.strategy.*;
import rental.handover.*;

/** Each subsystem works on its own, without the facade and without the other two. */
class SubsystemsStandaloneTest {
    static final LocalDate D1 = LocalDate.of(2030, 1, 1), D4 = LocalDate.of(2030, 1, 4);

    @TempDir Path dir;

    // ---------------- A ----------------
    FleetAvailabilityTracker fleet() { return new FleetAvailabilityTracker(new ReservationRepository(dir), new PricingEngine(() -> 1.0)); }

    @Test void fleetReservesAndPricesCar() {
        Reservation r = fleet().reserve(1, "Economy", D1, D4);
        assertEquals(90.0, r.getPrice());
        assertEquals(Reservation.Status.HELD, r.getStatus());
    }

    @Test void fleetDoesNotDoubleBookTheOnlyLuxuryCar() {
        var f = fleet();
        f.reserve(1, "Luxury", D1, D4);
        assertThrows(NoCarAvailableException.class, () -> f.reserve(2, "Luxury", D1.plusDays(1), D4.plusDays(1)));
    }

    @Test void fleetReleaseFreesTheCarAndIsIdempotent() {
        var f = fleet();
        f.reserve(1, "Luxury", D1, D4);
        f.releaseByOrder(1);
        f.releaseByOrder(1);
        assertDoesNotThrow(() -> f.reserve(2, "Luxury", D1, D4));
    }

    @Test void reservationRejectsIllegalTransition() {
        var f = fleet();
        f.reserve(1, "Economy", D1, D4);
        f.releaseByOrder(1);
        assertThrows(IllegalStateException.class, () -> f.confirm(1));   // RELEASED -> CONFIRMED is illegal
    }

    @Test void confirmWithoutReservationFails() { assertThrows(IllegalStateException.class, () -> fleet().confirm(77)); }

    @Test void fleetListsSeededCars() { assertEquals(4, fleet().cars().size()); }

    @Test void pricingGivesWeeklyDiscountAndRejectsBadDates() {
        var p = new PricingEngine(() -> 1.0);
        assertEquals(189.0, p.price("SUV", 30, D1, D1.plusDays(7)));      // 210 * 0.9 (assuming SUV uses LongTermPricingStrategy)
        assertThrows(IllegalArgumentException.class, () -> p.price("SUV", 30, D4, D1));
        assertThrows(IllegalArgumentException.class, () -> PricingEngine.days(null, D1));
    }

    // ---------------- B ----------------
    DepositHandler billing() { return new DepositHandler(new BillingRepository(dir), new InsuranceCalculator(() -> false)); }

    @Test void billingHoldsDepositAndQuotesPolicy() {
        var b = billing();
        Deposit d = b.authorize(1, 90, 3, 30, InsurancePolicy.Coverage.BASIC, 1000);
        assertEquals(100.0, d.getAmount());                        // min deposit 100
        assertEquals(15.0, b.policyOf(1).orElseThrow().getPremium());
        assertEquals(InsurancePolicy.Status.QUOTED, b.policyOf(1).orElseThrow().getStatus());
    }

    @Test void billingDeclinesWhenCardTooPoorAndPersistsNothing() {
        var b = billing();
        assertThrows(DepositDeclinedException.class, () -> b.authorize(1, 90, 3, 30, InsurancePolicy.Coverage.BASIC, 50));
        assertTrue(b.depositOf(1).isEmpty());
        assertTrue(b.policyOf(1).isEmpty());
    }

    @Test void billingRefundCancelsEverythingAndIsIdempotent() {
        var b = billing();
        b.authorize(1, 90, 3, 30, InsurancePolicy.Coverage.BASIC, 1000);
        b.refundByOrder(1);
        b.refundByOrder(1);
        assertEquals(Deposit.Status.REFUNDED, b.depositOf(1).orElseThrow().getStatus());
        assertEquals(InsurancePolicy.Status.CANCELLED, b.policyOf(1).orElseThrow().getStatus());
    }

    @Test void billingActivateMovesPolicyToActive() {
        var b = billing();
        b.authorize(1, 90, 3, 30, InsurancePolicy.Coverage.FULL, 1000);
        b.activate(1);
        assertEquals(InsurancePolicy.Status.ACTIVE, b.policyOf(1).orElseThrow().getStatus());
    }

    @Test void depositAndPolicyRejectIllegalTransitions() {
        var d = new Deposit(1, 1, 100, Deposit.Status.REFUNDED);
        assertThrows(IllegalStateException.class, () -> d.moveTo(Deposit.Status.CAPTURED));
        var p = new InsurancePolicy(1, 1, InsurancePolicy.Coverage.BASIC, 10, InsurancePolicy.Status.CANCELLED);
        assertThrows(IllegalStateException.class, () -> p.moveTo(InsurancePolicy.Status.ACTIVE));
        var held = new Deposit(2, 1, 100, Deposit.Status.HELD);
        held.moveTo(Deposit.Status.CAPTURED);
        assertEquals(Deposit.Status.CAPTURED, held.getStatus());
    }

    @Test void insuranceCalculatorRulesAndDepositRules() {
        var c = new InsuranceCalculator(() -> false);
        assertEquals(15.0, c.premium(InsurancePolicy.Coverage.BASIC, 3, 30));
        assertEquals(54.0, c.premium(InsurancePolicy.Coverage.FULL, 3, 20));    // 36 * 1.5 young driver
        assertThrows(IllegalArgumentException.class, () -> c.premium(InsurancePolicy.Coverage.BASIC, 3, 17));
        var b = billing();
        assertEquals(200.0, b.depositAmount(1000, InsurancePolicy.Coverage.BASIC));
        assertEquals(100.0, b.depositAmount(1000, InsurancePolicy.Coverage.FULL));
    }

    // ---------------- C ----------------
    @Test void inspectorPassesCleanCarAndFailsDamagedCar() {
        var i = new DamageInspector(new HandoverRepository(dir), new DamageRules());
        assertEquals(Inspection.Status.PASSED, i.inspect(1, 1, 10).getStatus());
        assertThrows(DamageFoundException.class, () -> i.inspect(2, 1, 90));
        assertEquals(Inspection.Status.FAILED, i.inspectionOf(2).orElseThrow().getStatus());
    }

    @Test void inspectionVoidingWorksOnlyForPassedAndIsIdempotent() {
        var i = new DamageInspector(new HandoverRepository(dir), new DamageRules());
        i.inspect(1, 1, 10);
        i.voidByOrder(1);
        i.voidByOrder(1);
        assertEquals(Inspection.Status.VOIDED, i.inspectionOf(1).orElseThrow().getStatus());
        assertThrows(DamageFoundException.class, () -> i.inspect(2, 1, 90));
        i.voidByOrder(2);                                           // FAILED stays FAILED
        assertEquals(Inspection.Status.FAILED, i.inspectionOf(2).orElseThrow().getStatus());
    }

    @Test void damageRulesValidateRange() {
        var r = new DamageRules(30);
        assertTrue(r.isAcceptable(30));
        assertFalse(r.isAcceptable(31));
        assertThrows(IllegalArgumentException.class, () -> r.isAcceptable(101));
        assertThrows(IllegalArgumentException.class, () -> r.isAcceptable(-1));
    }

    @Test void gpsPoolRunsOutAndReleaseFreesDevice() {
        var g = new GpsTracker(new HandoverRepository(dir, 2));
        g.activate(1); g.activate(2);
        assertThrows(GpsUnavailableException.class, () -> g.activate(3));
        g.releaseByOrder(1);
        assertEquals("GPS-001", g.activate(3));
        assertTrue(g.deviceOf(2).isPresent());
    }

    @Test void inspectionRejectsIllegalTransition() {
        var ins = new Inspection(1, 1, 1, 10, Inspection.Status.FAILED);
        assertThrows(IllegalStateException.class, () -> ins.moveTo(Inspection.Status.PASSED));
    }
}