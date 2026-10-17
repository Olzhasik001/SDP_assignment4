package rental.fleet;

/** A car in the fleet (immutable value object). */
public record Car(long id, String type, String plate, double dailyRate) { }
