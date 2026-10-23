package rental.app;

import rental.billing.InsurancePolicy;
import rental.billing.strategy.*;
import rental.fleet.strategy.*;

/** Assignment 5 demo: all strategies, manual vs automatic choice, runtime switching. */
public class StrategyDemo {

    public static void main(String[] args) {
        // simulated external data sources (can be changed while the demo runs)
        double[] demand = {1.5};
        boolean[] storm = {false};
        FleetDemandService demandService = () -> demand[0];
        WeatherService weatherService = () -> storm[0];

        double dailyRate = 100.0;
        long days = 10;

        System.out.println("=== 1. SAME INPUT, ALL PRICING STRATEGIES (rate " + dailyRate + ", " + days + " days, demand x" + demand[0] + ") ===");
        PricingStrategy[] pricing = {new StandardPricingStrategy(), new LongTermPricingStrategy(), new HighDemandPricingStrategy(demandService)};
        for (PricingStrategy s : pricing) {
            System.out.printf("  %-28s -> %8.2f%n", s.getClass().getSimpleName(), new PricingContext(s).executePricing(dailyRate, days));
        }

        System.out.println("\n=== 2. MANUAL CHOICE vs AUTOMATIC CHOICE (Selector) ===");
        PricingContext manual = new PricingContext(new LongTermPricingStrategy());     // manual: I pick the strategy
        System.out.printf("  manual    : %-26s -> %.2f%n", "LongTermPricingStrategy", manual.executePricing(dailyRate, days));
        PricingSelector pricingSelector = new PricingSelector(demandService);
        for (String carType : new String[] {"Economy", "SUV", "Luxury"}) {
            PricingStrategy chosen = pricingSelector.selectStrategy(carType);          // automatic: the Selector picks
            System.out.printf("  automatic : %-8s -> %-26s -> %.2f%n", carType, chosen.getClass().getSimpleName(),
                    new PricingContext(chosen).executePricing(dailyRate, days));
        }

        System.out.println("\n=== 3. SWITCHING THE STRATEGY AT RUNTIME (one context object) ===");
        PricingContext context = new PricingContext(new StandardPricingStrategy());
        System.out.printf("  Standard   : %.2f%n", context.executePricing(dailyRate, days));
        context.setStrategy(new LongTermPricingStrategy());
        System.out.printf("  -> LongTerm: %.2f%n", context.executePricing(dailyRate, days));
        context.setStrategy(new HighDemandPricingStrategy(demandService));
        System.out.printf("  -> HighDemand, demand x%.1f: %.2f%n", demand[0], context.executePricing(dailyRate, days));
        demand[0] = 2.0;                                                               // the data source changes, no code changes
        System.out.printf("  -> HighDemand, demand x%.1f: %.2f   (data source changed at runtime)%n", demand[0], context.executePricing(dailyRate, days));

        int driverAge = 22;
        System.out.println("\n=== 4. SAME INPUT, ALL INSURANCE STRATEGIES (" + days + " days, driver age " + driverAge + ", storm=" + storm[0] + ") ===");
        InsuranceStrategy[] insurance = {new BasicInsuranceStrategy(), new FullInsuranceStrategy(), new DynamicWeatherInsuranceStrategy(weatherService)};
        for (InsuranceStrategy s : insurance) {
            System.out.printf("  %-34s -> %8.2f%n", s.getClass().getSimpleName(), new InsuranceContext(s).executePremiumCalculation(days, driverAge));
        }

        System.out.println("\n=== 5. INSURANCE: AUTOMATIC CHOICE + RUNTIME SWITCH ===");
        InsuranceSelector insuranceSelector = new InsuranceSelector(weatherService);
        for (InsurancePolicy.Coverage coverage : InsurancePolicy.Coverage.values()) {
            InsuranceStrategy chosen = insuranceSelector.selectStrategy(coverage);
            System.out.printf("  automatic : %-6s -> %-30s -> %.2f%n", coverage, chosen.getClass().getSimpleName(),
                    new InsuranceContext(chosen).executePremiumCalculation(days, driverAge));
        }
        InsuranceContext insuranceContext = new InsuranceContext(new BasicInsuranceStrategy());
        System.out.printf("  Basic            : %.2f%n", insuranceContext.executePremiumCalculation(days, driverAge));
        insuranceContext.setStrategy(new DynamicWeatherInsuranceStrategy(weatherService));
        System.out.printf("  -> Weather, calm : %.2f%n", insuranceContext.executePremiumCalculation(days, driverAge));
        storm[0] = true;                                                               // storm warning arrives
        System.out.printf("  -> Weather, STORM: %.2f   (data source changed at runtime)%n", insuranceContext.executePremiumCalculation(days, driverAge));
    }
}