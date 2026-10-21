package rental.billing.strategy;
public class DynamicWeatherInsuranceStrategy implements InsuranceStrategy {
    private final WeatherService weather;
    public DynamicWeatherInsuranceStrategy(WeatherService weather) { this.weather = weather; }
    @Override public double calculatePremium(long days, int driverAge) {
        double base = 8.0 * days;
        return weather.isStormWarning() ? base * 2.0 : base;
    }
}