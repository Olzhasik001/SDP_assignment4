package rental.fleet.strategy;
public class HighDemandPricingStrategy implements PricingStrategy {
    private final FleetDemandService demandService;
    public HighDemandPricingStrategy(FleetDemandService demandService) { this.demandService = demandService; }
    @Override public double calculatePrice(double dailyRate, long days) {
        return dailyRate * days * demandService.getCurrentDemandMultiplier();
    }
}