package rental.billing.strategy;
public class InsuranceContext {
    private InsuranceStrategy strategy;
    public InsuranceContext(InsuranceStrategy strategy) { this.strategy = strategy; }
    public double executePremiumCalculation(long days, int driverAge) {
        return Math.round(strategy.calculatePremium(days, driverAge) * 100.0) / 100.0;
    }
}