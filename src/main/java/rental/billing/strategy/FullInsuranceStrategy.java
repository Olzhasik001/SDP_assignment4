package rental.billing.strategy;
public class FullInsuranceStrategy implements InsuranceStrategy {
    @Override public double calculatePremium(long days, int driverAge) {
        double total = 12.0 * days;
        return days > 7 ? total * 0.8 : total;
    }
}