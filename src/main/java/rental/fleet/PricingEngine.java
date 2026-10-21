package rental.fleet;

import rental.fleet.strategy.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class PricingEngine {
    private final PricingSelector selector;

    public PricingEngine(FleetDemandService demandService) {
        this.selector = new PricingSelector(demandService);
    }

    public static long days(LocalDate from, LocalDate to) {
        if (from == null || to == null || !to.isAfter(from)) {
            throw new IllegalArgumentException("Return date must be after pick-up date");
        }
        return ChronoUnit.DAYS.between(from, to);
    }

    public double price(String carType, double dailyRate, LocalDate from, LocalDate to) {
        long days = days(from, to);
        PricingStrategy strategy = selector.selectStrategy(carType);
        PricingContext context = new PricingContext(strategy);
        return context.executePricing(dailyRate, days);
    }
}