package rental.billing;

import java.util.Optional;

/** Public entry point of subsystem B. Knows nothing about fleet or handover. */
public class DepositHandler {
    private final BillingRepository repo;
    private final InsuranceCalculator calculator;

    public DepositHandler(BillingRepository repo, InsuranceCalculator calculator) {
        this.repo = repo; this.calculator = calculator;
    }

    /** Deposit = 20% of the rental price (min 100); FULL coverage halves it. */
    public double depositAmount(double rentalPrice, InsurancePolicy.Coverage coverage) {
        double d = Math.max(100.0, rentalPrice * 0.20);
        if (coverage == InsurancePolicy.Coverage.FULL) d /= 2;
        return Math.round(d * 100.0) / 100.0;
    }

    /** Step B: quote insurance + hold deposit. Nothing is persisted if the card cannot cover it. */
    public Deposit authorize(long orderRef, double rentalPrice, long days, int driverAge,
                             InsurancePolicy.Coverage coverage, double cardBalance) {
        double premium = calculator.premium(coverage, days, driverAge);
        double deposit = depositAmount(rentalPrice, coverage);
        if (cardBalance < deposit + premium) {
            throw new DepositDeclinedException("Card balance " + cardBalance + " is less than deposit "
                    + deposit + " + insurance " + premium);
        }
        repo.insertPolicy(orderRef, coverage, premium);
        return repo.insertDeposit(orderRef, deposit);
    }

    /** Called when the whole rental completes: policy becomes ACTIVE (deposit stays HELD until the car is returned). */
    public void activate(long orderRef) {
        InsurancePolicy p = repo.findPolicy(orderRef).orElseThrow(() ->
                new IllegalStateException("No policy for order " + orderRef));
        p.moveTo(InsurancePolicy.Status.ACTIVE);
        repo.update(p);
    }

    /** Compensation. Idempotent: refunds the deposit and cancels the policy if they exist. */
    public void refundByOrder(long orderRef) {
        repo.findDeposit(orderRef).ifPresent(d -> {
            if (d.getStatus() == Deposit.Status.HELD) { d.moveTo(Deposit.Status.REFUNDED); repo.update(d); }
        });
        repo.findPolicy(orderRef).ifPresent(p -> {
            if (p.getStatus() != InsurancePolicy.Status.CANCELLED) { p.moveTo(InsurancePolicy.Status.CANCELLED); repo.update(p); }
        });
    }

    public Optional<Deposit> depositOf(long orderRef) { return repo.findDeposit(orderRef); }
    public Optional<InsurancePolicy> policyOf(long orderRef) { return repo.findPolicy(orderRef); }
}
