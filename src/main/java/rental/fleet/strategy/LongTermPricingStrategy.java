package rental.fleet.strategy;
public class LongTermPricingStrategy implements PricingStrategy {
    @Override public double calculatePrice(double dailyRate, long days) {
        double total = dailyRate * days;
        if (days >= 14) return total * 0.8;
        if (days >= 7) return total * 0.9;
        return total;
    }
}