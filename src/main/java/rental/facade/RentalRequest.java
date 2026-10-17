package rental.facade;

import java.time.LocalDate;
import rental.billing.InsurancePolicy;

/**
 * Everything the client provides. inspectionDamageScore simulates the result of the
 * physical inspection at hand-over (0 = perfect, 100 = wrecked).
 */
public record RentalRequest(String customer, String carType, LocalDate from, LocalDate to, int driverAge,
                            InsurancePolicy.Coverage coverage, double cardBalance, int inspectionDamageScore) { }
