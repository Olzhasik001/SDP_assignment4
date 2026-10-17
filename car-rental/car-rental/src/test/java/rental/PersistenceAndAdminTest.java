package rental;

import static org.junit.jupiter.api.Assertions.*;
import static rental.facade.RentalOrder.ChainStatus.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rental.billing.*;
import rental.facade.*;
import rental.fleet.*;
import rental.handover.*;

/** Restart / persistence-reload tests and the admin facade. */
class PersistenceAndAdminTest {
    @TempDir Path dir;
    RentalDsl rental;

    @BeforeEach void setUp() { rental = new RentalDsl(dir); }

    @Test void completedRentalSurvivesRestart() {
        RentalOrder o = rental.aRental().book();
        rental.restartApplication();
        rental.thenOrderIs(o.getId(), COMPLETED).andReservationIs(Reservation.Status.CONFIRMED).andPolicyIs(InsurancePolicy.Status.ACTIVE);
    }

    @Test void restartWhileChainIsPendingCUndoesBThenA() {
        RentalOrder o = rental.system.rental().rentCarAndCrashBeforeC(rental.aRental().ofType("Luxury").build());
        assertEquals(PENDING_C, o.getStatus());

        rental.restartApplication();                       // all in-memory state is gone; only files remain
        rental.thenLatestOrderIs(PENDING_C);
        List<RentalOrder> recovered = rental.system.admin().recoverPending();

        assertEquals(1, recovered.size());
        rental.thenLatestOrderIs(COMPENSATED)
              .andReservationIs(Reservation.Status.RELEASED)
              .andDepositIs(Deposit.Status.REFUNDED)
              .andPolicyIs(InsurancePolicy.Status.CANCELLED);
        assertDoesNotThrow(() -> rental.aRental().ofType("Luxury").book());   // car is free again
    }

    @Test void restartWithPendingBUndoesOnlyA() {
        // build a PENDING_B order by hand: A committed, B never ran
        var orders = new RentalOrderRepository(dir);
        var req = rental.aRental().ofType("SUV").build();
        RentalOrder o = orders.create(req);
        rental.system.fleet().reserve(o.getId(), "SUV", req.from(), req.to());
        orders.update(o.getId(), PENDING_B, "A done");

        rental.restartApplication();
        rental.system.admin().recoverPending();

        rental.thenLatestOrderIs(COMPENSATED).andReservationIs(Reservation.Status.RELEASED).andNoDepositExists();
    }

    @Test void restartWithStartedOrderReleasesPossiblyCommittedReservation() {
        var orders = new RentalOrderRepository(dir);
        var req = rental.aRental().ofType("SUV").build();
        RentalOrder o = orders.create(req);                                   // crashed right after A committed
        rental.system.fleet().reserve(o.getId(), "SUV", req.from(), req.to()); // but before PENDING_B was written
        rental.restartApplication();
        rental.system.admin().recoverPending();
        rental.thenLatestOrderIs(COMPENSATED).andReservationIs(Reservation.Status.RELEASED);
    }

    @Test void recoveryDoesNothingWhenNothingIsPending() {
        rental.aRental().book();
        rental.restartApplication();
        assertTrue(rental.system.admin().recoverPending().isEmpty());
        rental.thenLatestOrderIs(COMPLETED);
    }

    @Test void adminInspectShowsStateOfAllThreeSubsystems() {
        RentalOrder o = rental.aRental().book();
        String report = rental.system.admin().inspect(o.getId());
        assertTrue(report.contains("COMPLETED"));
        assertTrue(report.contains("CONFIRMED"));
        assertTrue(report.contains("HELD"));
        assertTrue(report.contains("PASSED"));
        assertTrue(report.contains("GPS-001"));
        assertThrows(IllegalArgumentException.class, () -> rental.system.admin().inspect(999));
    }

    @Test void adminCanManuallyCompensateCompletedOrderOnlyOnce() {
        RentalOrder o = rental.aRental().ofType("SUV").book();
        RentalOrder after = rental.system.admin().manualCompensate(o.getId());
        assertEquals(COMPENSATED, after.getStatus());
        rental.andReservationIs(Reservation.Status.RELEASED)
              .andDepositIs(Deposit.Status.REFUNDED)
              .andPolicyIs(InsurancePolicy.Status.CANCELLED)
              .andInspectionIs(Inspection.Status.VOIDED)
              .andNoGpsIsAssigned();
        assertThrows(IllegalStateException.class, () -> rental.system.admin().manualCompensate(o.getId()));
        assertThrows(IllegalArgumentException.class, () -> rental.system.admin().manualCompensate(999));
    }

    @Test void orderRepositoryFindsByIdAndStatus() {
        var orders = new RentalOrderRepository(dir);
        var o = orders.create(rental.aRental().build());
        assertEquals(STARTED, orders.findById(o.getId()).orElseThrow().getStatus());
        assertEquals(1, orders.findByStatus(STARTED).size());
        assertTrue(orders.findById(12345).isEmpty());
        assertNotNull(o.getCustomer());
        assertNotNull(o.getCarType());
        assertNotNull(o.getFrom());
        assertNotNull(o.getTo());
        assertNotNull(o.getNote());
    }
}
