package rental.fleet.strategy;
public class PricingContext {
    private PricingStrategy strategy;
    public PricingContext(PricingStrategy strategy) { this.strategy = strategy; }
    public void setStrategy(PricingStrategy strategy) { this.strategy = strategy; }
    public double executePricing(double dailyRate, long days) {
        return Math.round(strategy.calculatePrice(dailyRate, days) * 100.0) / 100.0;
    }
}