package rental.billing.strategy;
public class FullInsuranceStrategy implements InsuranceStrategy {
    @Override public double calculatePremium(long days, int driverAge) {
        double total = 12.0 * days;
        if (days > 7) total *= 0.8;                 // long-duration discount
        return driverAge < 25 ? total * 1.5 : total; // young driver surcharge (original rule)
    }
}