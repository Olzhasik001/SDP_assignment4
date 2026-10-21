package rental.billing.strategy;
public class BasicInsuranceStrategy implements InsuranceStrategy {
    @Override public double calculatePremium(long days, int driverAge) {
        return driverAge < 25 ? (5.0 * days) * 1.5 : 5.0 * days;
    }
}