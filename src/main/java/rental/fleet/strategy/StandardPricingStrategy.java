package rental.fleet.strategy;
public class StandardPricingStrategy implements PricingStrategy {
    @Override public double calculatePrice(double dailyRate, long days) { return dailyRate * days; }
}