package rental.handover;

import java.nio.file.Path;

/** Subsystem C used completely on its own (no facade, no A, no B). */
public class HandoverMain {
    public static void main(String[] args) {
        var repo = new HandoverRepository(Path.of("data-standalone"));
        var inspector = new DamageInspector(repo, new DamageRules());
        var gps = new GpsTracker(repo);
        System.out.println("Standalone C: " + inspector.inspect(999, 1, 10) + " / GPS " + gps.activate(999));
        inspector.voidByOrder(999);
        gps.releaseByOrder(999);
        System.out.println("After cleanup: " + inspector.inspectionOf(999).orElseThrow() + " / GPS " + gps.deviceOf(999));
    }
}
