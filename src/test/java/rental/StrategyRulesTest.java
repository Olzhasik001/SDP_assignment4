package rental;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import rental.billing.InsurancePolicy;
import rental.billing.strategy.*;
import rental.fleet.strategy.*;

/** Fills the gaps: every strategy, every Selector rule, both contexts. */
class StrategyRulesTest {
    WeatherService fakeStorm = () -> true;
    FleetDemandService fakeDemand = () -> 1.5;

    // ---- every strategy ----
    @Test void standardPricingMultipliesRateByDays() {
        assertEquals(500.0, new PricingContext(new StandardPricingStrategy()).executePricing(100, 5));
    }
    @Test void longTermPricingGivesTenPercentFromSevenDays() {
        var ctx = new PricingContext(new LongTermPricingStrategy());
        assertEquals(600.0, ctx.executePricing(100, 6));      // no discount
        assertEquals(630.0, ctx.executePricing(100, 7));      // -10%
    }
    @Test void longTermPricingGivesTwentyPercentFromFourteenDays() {
        assertEquals(1120.0, new PricingContext(new LongTermPricingStrategy()).executePricing(100, 14));
    }
    @Test void fullInsuranceHasLongDurationDiscountAndYoungDriverSurcharge() {
        var ctx = new InsuranceContext(new FullInsuranceStrategy());
        assertEquals(84.0, ctx.executePremiumCalculation(7, 30));     // 7 days: no discount
        assertEquals(76.8, ctx.executePremiumCalculation(8, 30));     // > 7 days: -20%
        assertEquals(54.0, ctx.executePremiumCalculation(3, 20));     // under 25: x1.5
    }
    @Test void dynamicWeatherInsuranceIsCheaperWithoutStorm() {
        assertEquals(80.0, new InsuranceContext(new DynamicWeatherInsuranceStrategy(() -> false)).executePremiumCalculation(10, 30));
    }

    // ---- every Selector rule ----
    @Test void pricingSelector_economyGetsStandard() { assertInstanceOf(StandardPricingStrategy.class, new PricingSelector(fakeDemand).selectStrategy("Economy")); }
    @Test void pricingSelector_suvGetsLongTerm()      { assertInstanceOf(LongTermPricingStrategy.class, new PricingSelector(fakeDemand).selectStrategy("SUV")); }
    @Test void pricingSelector_luxuryGetsHighDemand() { assertInstanceOf(HighDemandPricingStrategy.class, new PricingSelector(fakeDemand).selectStrategy("Luxury")); }
    @Test void pricingSelector_unknownTypeFallsBackToStandard() { assertInstanceOf(StandardPricingStrategy.class, new PricingSelector(fakeDemand).selectStrategy("Helicopter")); }
    @Test void pricingSelector_ignoresLetterCase()    { assertInstanceOf(LongTermPricingStrategy.class, new PricingSelector(fakeDemand).selectStrategy("suv")); }
    @Test void insuranceSelector_basicGetsBasic() { assertInstanceOf(BasicInsuranceStrategy.class, new InsuranceSelector(fakeStorm).selectStrategy(InsurancePolicy.Coverage.BASIC)); }
    @Test void insuranceSelector_fullGetsFull()   { assertInstanceOf(FullInsuranceStrategy.class, new InsuranceSelector(fakeStorm).selectStrategy(InsurancePolicy.Coverage.FULL)); }

    // ---- contexts ----
    @Test void insuranceContextSwitchesStrategyAtRuntime() {
        var ctx = new InsuranceContext(new BasicInsuranceStrategy());
        assertEquals(50.0, ctx.executePremiumCalculation(10, 30));
        ctx.setStrategy(new DynamicWeatherInsuranceStrategy(fakeStorm));
        assertEquals(160.0, ctx.executePremiumCalculation(10, 30));
    }
    @Test void highDemandPricingFollowsTheDataSource() {
        double[] multiplier = {1.0};
        var ctx = new PricingContext(new HighDemandPricingStrategy(() -> multiplier[0]));
        assertEquals(300.0, ctx.executePricing(100, 3));
        multiplier[0] = 2.0;
        assertEquals(600.0, ctx.executePricing(100, 3));
    }
}