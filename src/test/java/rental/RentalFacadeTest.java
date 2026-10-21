package rental;

import static org.junit.jupiter.api.Assertions.*;
import static rental.facade.RentalOrder.ChainStatus.*;

import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rental.billing.*;
import rental.facade.*;
import rental.fleet.*;
import rental.handover.*;

/** Facade behaviour. Every test gets its own empty temp directory -> independent and repeatable. */
class RentalFacadeTest {
    @TempDir Path dir;
    RentalDsl rental;

    @BeforeEach void setUp() { rental = new RentalDsl(dir); }

    @Test void happyPathCompletesAllThreeSteps() {
        RentalOrder o = rental.aRental().ofType("SUV").book();
        assertEquals(COMPLETED, o.getStatus());
        rental.thenLatestOrderIs(COMPLETED)
              .andReservationIs(Reservation.Status.CONFIRMED)
              .andDepositIs(Deposit.Status.HELD)
              .andPolicyIs(InsurancePolicy.Status.ACTIVE)
              .andInspectionIs(Inspection.Status.PASSED)
              .andGpsIsAssigned();
    }

    // ---- compensation depth 1: B fails after A committed -> only A is undone ----
    @Test void whenBFailsAfterAOnlyAIsCompensated() {
        rental.aRental().withCardBalance(10).bookExpectingFailure(DepositDeclinedException.class);
        rental.thenLatestOrderIs(COMPENSATED)
              .andReservationIs(Reservation.Status.RELEASED)
              .andNoDepositExists()
              .andNoInspectionExists()
              .andNoGpsIsAssigned();
    }

    @Test void carBecomesBookableAgainAfterBFailure() {
        rental.aRental().ofType("Luxury").withCardBalance(10).bookExpectingFailure(DepositDeclinedException.class);
        assertEquals(COMPLETED, rental.aRental().ofType("Luxury").book().getStatus());
    }

    // ---- compensation depth 2: C fails after A and B committed -> B then A are undone ----
    @Test void whenCFailsBecauseOfDamageBThenAAreCompensated() {
        rental.aRental().withDamageScore(80).bookExpectingFailure(DamageFoundException.class);
        rental.thenLatestOrderIs(COMPENSATED)
              .andReservationIs(Reservation.Status.RELEASED)
              .andDepositIs(Deposit.Status.REFUNDED)
              .andPolicyIs(InsurancePolicy.Status.CANCELLED)
              .andInspectionIs(Inspection.Status.FAILED)
              .andNoGpsIsAssigned();
    }

    @Test void whenCFailsBecauseGpsPoolIsEmptyEverythingIsUndoneIncludingPassedInspection() {
        rental.aRental().ofType("Economy").startingOn(LocalDate.of(2031, 1, 1)).book();
        rental.aRental().ofType("Economy").startingOn(LocalDate.of(2031, 1, 1)).book();
        rental.aRental().ofType("SUV").startingOn(LocalDate.of(2031, 1, 1)).book();     // 3 GPS devices now busy
        rental.aRental().ofType("Luxury").bookExpectingFailure(GpsUnavailableException.class);
        rental.thenLatestOrderIs(COMPENSATED)
              .andReservationIs(Reservation.Status.RELEASED)
              .andDepositIs(Deposit.Status.REFUNDED)
              .andPolicyIs(InsurancePolicy.Status.CANCELLED)
              .andInspectionIs(Inspection.Status.VOIDED)
              .andNoGpsIsAssigned();
    }

    // ---- other failure cases ----
    @Test void whenAFailsNothingIsCommittedAnywhere() {
        rental.aRental().ofType("Helicopter").bookExpectingFailure(NoCarAvailableException.class);
        rental.thenLatestOrderIs(FAILED).andNoDepositExists().andNoInspectionExists().andNoGpsIsAssigned();
        assertTrue(rental.system.fleet().reservationOf(rental.latestOrder().getId()).isEmpty());
    }

    @Test void invalidInputIsRejectedBeforeAnythingIsPersisted() {
        rental.aRental().forDays(-2).bookExpectingFailure(InvalidRentalRequestException.class);
        rental.aRental().driverAged(16).bookExpectingFailure(InvalidRentalRequestException.class);
        rental.aRental().withDamageScore(500).bookExpectingFailure(InvalidRentalRequestException.class);
        assertTrue(rental.system.admin().listOrders().isEmpty());
        assertThrows(InvalidRentalRequestException.class, () -> rental.system.rental().rentCar(null));
        var good = rental.aRental().build();
        assertThrows(InvalidRentalRequestException.class, () -> rental.system.rental().rentCar(
                new RentalRequest(" ", good.carType(), good.from(), good.to(), 30, good.coverage(), 100, 0)));
        assertThrows(InvalidRentalRequestException.class, () -> rental.system.rental().rentCar(
                new RentalRequest("X", "", good.from(), good.to(), 30, good.coverage(), 100, 0)));
        assertThrows(InvalidRentalRequestException.class, () -> rental.system.rental().rentCar(
                new RentalRequest("X", "SUV", good.from(), good.to(), 30, null, 100, 0)));
        assertThrows(InvalidRentalRequestException.class, () -> rental.system.rental().rentCar(
                new RentalRequest("X", "SUV", good.from(), good.to(), 30, good.coverage(), -1, 0)));
    }

    @Test void secondBookingOfSameCarAndDatesFailsAtStepA() {
        rental.aRental().ofType("Luxury").book();
        rental.aRental().ofType("Luxury").bookExpectingFailure(NoCarAvailableException.class);
        rental.thenLatestOrderIs(FAILED);
    }

}
