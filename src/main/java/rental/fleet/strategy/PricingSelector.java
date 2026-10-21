package rental.fleet.strategy;
public class PricingSelector {
    private final FleetDemandService demandService;
    public PricingSelector(FleetDemandService demandService) { this.demandService = demandService; }
    public PricingStrategy selectStrategy(String carType) {
        return switch (carType.toUpperCase()) {
            case "ECONOMY" -> new StandardPricingStrategy();
            case "SUV" -> new LongTermPricingStrategy();
            case "LUXURY" -> new HighDemandPricingStrategy(demandService);
            default -> new StandardPricingStrategy();
        };
    }
}