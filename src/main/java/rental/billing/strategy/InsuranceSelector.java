package rental.billing.strategy;
import rental.billing.InsurancePolicy;

public class InsuranceSelector {
    private final WeatherService weatherService;
    public InsuranceSelector(WeatherService weatherService) { this.weatherService = weatherService; }
    public InsuranceStrategy selectStrategy(InsurancePolicy.Coverage coverage) {
        return switch (coverage) {
            case BASIC -> new BasicInsuranceStrategy();
            case FULL -> new FullInsuranceStrategy();
        };
    }
}