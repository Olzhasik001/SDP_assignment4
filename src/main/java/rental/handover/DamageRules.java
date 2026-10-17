package rental.handover;

/** Pure business logic of subsystem C: is the damage level acceptable? */
public class DamageRules {
    private final int maxAcceptableScore;

    public DamageRules() { this(30); }
    public DamageRules(int maxAcceptableScore) { this.maxAcceptableScore = maxAcceptableScore; }

    public boolean isAcceptable(int damageScore) {
        if (damageScore < 0 || damageScore > 100) {
            throw new IllegalArgumentException("Damage score must be 0..100");
        }
        return damageScore <= maxAcceptableScore;
    }
}
