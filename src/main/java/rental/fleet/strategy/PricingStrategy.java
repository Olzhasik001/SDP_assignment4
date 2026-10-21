package rental.fleet.strategy;
public interface PricingStrategy { double calculatePrice(double dailyRate, long days); }