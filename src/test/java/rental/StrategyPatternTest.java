package rental;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import rental.billing.strategy.*;
import rental.fleet.strategy.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class StrategyPatternTest {

    WeatherService fakeWeather = () -> true;
    FleetDemandService fakeDemand = () -> 1.5;

    @Test
    void testBasicInsurance() {
        InsuranceContext ctx = new InsuranceContext(new BasicInsuranceStrategy());
        assertEquals(75.0, ctx.executePremiumCalculation(10, 20)); 
    }

    @Test
    void testDynamicWeatherInsurance() {
        InsuranceContext ctx = new InsuranceContext(new DynamicWeatherInsuranceStrategy(fakeWeather));
        assertEquals(160.0, ctx.executePremiumCalculation(10, 30)); 
    }

    @Test
    void testHighDemandPricing() {
        PricingContext ctx = new PricingContext(new HighDemandPricingStrategy(fakeDemand));
        assertEquals(450.0, ctx.executePricing(100.0, 3)); 
    }

    @Test
    void runtimeStrategySwitching() {
        PricingContext ctx = new PricingContext(new StandardPricingStrategy());
        assertEquals(300.0, ctx.executePricing(100.0, 3));
        
        ctx.setStrategy(new HighDemandPricingStrategy(fakeDemand));
        assertEquals(450.0, ctx.executePricing(100.0, 3));
    }

    static Stream<InsuranceStrategy> insuranceProvider() {
        return Stream.of(new BasicInsuranceStrategy(), new FullInsuranceStrategy(), new DynamicWeatherInsuranceStrategy(() -> false));
    }

    @ParameterizedTest
    @MethodSource("insuranceProvider")
    void insuranceIsNeverNegative(InsuranceStrategy strategy) {
        InsuranceContext ctx = new InsuranceContext(strategy);
        assertTrue(ctx.executePremiumCalculation(5, 30) > 0);
    }

    static Stream<PricingStrategy> pricingProvider() {
        return Stream.of(new StandardPricingStrategy(), new LongTermPricingStrategy(), new HighDemandPricingStrategy(() -> 1.0));
    }

    @ParameterizedTest
    @MethodSource("pricingProvider")
    void pricingIsNeverNegative(PricingStrategy strategy) {
        PricingContext ctx = new PricingContext(strategy);
        assertTrue(ctx.executePricing(50.0, 5) > 0);
    }
}
