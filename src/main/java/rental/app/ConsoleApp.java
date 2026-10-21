package rental.app;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Scanner;
import rental.billing.InsurancePolicy;
import rental.facade.RentalOrder;
import rental.facade.RentalRequest;
/*add a brand-new facade method that runs only a and b,without c and decide live number whether it needs its own compensation logic*/
/** Console demo. Run: mvn -q compile exec:java   (reset data: delete the ./data folder). */
public class ConsoleApp {
    private static final Path DATA = Path.of("data");

    public static void main(String[] args) {
        RentalSystem sys = RentalSystem.open(DATA);
        Scanner in = new Scanner(System.in);
        System.out.println("=== Car Rental (Facade demo) === data dir: " + DATA.toAbsolutePath());
        // Restart recovery happens on startup, based only on what is on disk.
        var recovered = sys.admin().recoverPending();
        if (!recovered.isEmpty()) {
            System.out.println("Recovered unfinished orders after restart:");
            recovered.forEach(o -> System.out.println("  " + o));
        }
        while (true) {
            System.out.println("""

                1. Rent a car (interactive)
                2. Show all orders
                3. Inspect one order (A + B + C state from disk)
                4. Show fleet
                5. Admin: manual compensate order
                6. Admin: recover pending orders
                7. DEMO: B fails after A   (card balance too low)  -> undo A
                8. DEMO: C fails after A+B (damaged car)           -> undo B then A
                9. DEMO: A fails           (no such car type)      -> nothing to undo
               10. DEMO: C fails (GPS pool exhausted: first complete 3 rentals with option 1, then run this)
               11. DEMO: simulate CRASH after B (then exit and restart the app)
                0. Exit""");
            System.out.print("> ");
            String choice = in.nextLine().trim();
            try {
                switch (choice) {
                    case "1" -> System.out.println("RESULT: " + sys.rental().rentCar(ask(in)));
                    case "2" -> sys.admin().listOrders().forEach(System.out::println);
                    case "3" -> { System.out.print("Order id: "); System.out.println(sys.admin().inspect(Long.parseLong(in.nextLine().trim()))); }
                    case "4" -> sys.admin().fleet().forEach(System.out::println);
                    case "5" -> { System.out.print("Order id: "); System.out.println(sys.admin().manualCompensate(Long.parseLong(in.nextLine().trim()))); }
                    case "6" -> { var r = sys.admin().recoverPending(); System.out.println(r.isEmpty() ? "Nothing to recover" : ""); r.forEach(System.out::println); }
                    case "7" -> run(sys, preset("Economy", 10, 30, 5));
                    case "8" -> run(sys, preset("Economy", 5000, 30, 80));
                    case "9" -> run(sys, preset("Helicopter", 5000, 30, 5));
                    case "10" -> run(sys, preset("Luxury", 5000, 30, 5));
                    case "11" -> System.out.println("CRASHED before C: " + sys.rental().rentCarAndCrashBeforeC(preset("SUV", 5000, 30, 5)));
                    case "0" -> { return; }
                    default -> System.out.println("Unknown option");
                }
            } catch (RuntimeException e) {
                System.out.println("ERROR (" + e.getClass().getSimpleName() + "): " + e.getMessage());
            }
        }
    }

    private static void run(RentalSystem sys, RentalRequest req) {
        try {
            System.out.println("RESULT: " + sys.rental().rentCar(req));
        } catch (RuntimeException e) {
            System.out.println("FAILED (" + e.getClass().getSimpleName() + "): " + e.getMessage());
            var last = sys.admin().listOrders();
            System.out.println("Persisted state:\n" + sys.admin().inspect(last.get(last.size() - 1).getId()));
        }
    }

    private static RentalRequest preset(String type, double balance, int age, int damage) {
        LocalDate from = LocalDate.now().plusDays(1);
        return new RentalRequest("Demo Customer", type, from, from.plusDays(3), age, InsurancePolicy.Coverage.BASIC, balance, damage);
    }

    private static RentalRequest ask(Scanner in) {
        System.out.print("Customer name: "); String name = in.nextLine();
        System.out.print("Car type (Economy/SUV/Luxury): "); String type = in.nextLine().trim();
        System.out.print("Pick-up date (yyyy-MM-dd): "); LocalDate from = LocalDate.parse(in.nextLine().trim());
        System.out.print("Return date (yyyy-MM-dd): "); LocalDate to = LocalDate.parse(in.nextLine().trim());
        System.out.print("Driver age: "); int age = Integer.parseInt(in.nextLine().trim());
        System.out.print("Coverage (BASIC/FULL): "); var cov = InsurancePolicy.Coverage.valueOf(in.nextLine().trim().toUpperCase());
        System.out.print("Card balance: "); double bal = Double.parseDouble(in.nextLine().trim());
        System.out.print("Damage score found at inspection (0-100): "); int dmg = Integer.parseInt(in.nextLine().trim());
        return new RentalRequest(name, type, from, to, age, cov, bal, dmg);
    }
}
