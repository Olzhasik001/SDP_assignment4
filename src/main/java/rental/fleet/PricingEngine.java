package rental.fleet;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class PricingEngine {
    public static long days(LocalDate from, LocalDate to) {
        if (from == null || to == null || !to.isAfter(from)) {
            throw new IllegalArgumentException("Return date must be after pick-up date");
        }
        return ChronoUnit.DAYS.between(from, to);
    }

    public double price(double dailyRate, LocalDate from, LocalDate to) {
        long days = days(from, to);
        double total = dailyRate * days;
        if (days >= 7) total *= 0.9;
        return Math.round(total * 100.0) / 100.0;
    }
}