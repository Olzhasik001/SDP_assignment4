package rental.billing;

/** Pure business logic of subsystem B. */
public class InsuranceCalculator {

    public double premium(InsurancePolicy.Coverage coverage, long days, int driverAge) {
        if (driverAge < 18) throw new IllegalArgumentException("Driver must be at least 18");
        double perDay = coverage == InsurancePolicy.Coverage.FULL ? 12.0 : 5.0;
        double total = perDay * days;
        if (driverAge < 25) total *= 1.5;   // young driver surcharge
        return Math.round(total * 100.0) / 100.0;
    }
}
