package rental.billing;

import rental.billing.strategy.*;

public class InsuranceCalculator {
    private final InsuranceSelector selector;

    public InsuranceCalculator(WeatherService weatherService) {
        this.selector = new InsuranceSelector(weatherService);
    }

    public double premium(InsurancePolicy.Coverage coverage, long days, int driverAge) {
        if (driverAge < 18) throw new IllegalArgumentException("Driver must be at least 18");
        InsuranceStrategy strategy = selector.selectStrategy(coverage);
        InsuranceContext context = new InsuranceContext(strategy);
        return context.executePremiumCalculation(days, driverAge);
    }
}