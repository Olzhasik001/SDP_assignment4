package rental.billing;

import java.nio.file.Path;

/** Subsystem B used completely on its own (no facade, no A, no C). */
public class BillingMain {
    public static void main(String[] args) {
        var billing = new DepositHandler(new BillingRepository(Path.of("data-standalone")), new InsuranceCalculator());
        var d = billing.authorize(999, 90.0, 3, 30, InsurancePolicy.Coverage.BASIC, 1000);
        System.out.println("Standalone B: " + d + " / " + billing.policyOf(999).orElseThrow());
        billing.refundByOrder(999);
        System.out.println("After refund: " + billing.depositOf(999).orElseThrow() + " / " + billing.policyOf(999).orElseThrow());
    }
}
