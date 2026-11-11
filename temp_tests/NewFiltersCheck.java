import com.example.petregistry.db.DatabaseManager;
import com.example.petregistry.model.PetSize;
import com.example.petregistry.model.Reservation;
import com.example.petregistry.model.ServiceLocation;
import com.example.petregistry.model.ServiceStatus;
import com.example.petregistry.model.User;
import com.example.petregistry.repo.Repositories;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Checks the filters added for the "rest of the columns", on the demo data.
 * Usage: NewFiltersCheck           memory mode only (SeedData)
 *        NewFiltersCheck db [name] memory mode, then the same checks on MySQL (default pet_registry_test,
 *                                  loaded from docs/schema.sql WITH the demo block)
 */
public class NewFiltersCheck {

    static int pass;
    static int fail;

    public static void main(String[] args) {
        Repositories.useMemory();
        System.out.println("== memory ==");
        runAll();

        if (args.length > 0 && args[0].equals("db")) {
            String db = args.length > 1 ? args[1] : "pet_registry_test";
            DatabaseManager.connect("jdbc:mysql://localhost:3306/" + db, "root", "");
            Repositories.useDatabase();
            System.out.println("== MySQL " + db + " ==");
            runAll();
            DatabaseManager.disconnect();
        }
        System.out.println();
        System.out.println("PASSED " + pass + ", FAILED " + fail);
        System.exit(fail == 0 ? 0 : 1);
    }

    /** The same calls and expectations in both modes: memory and SQL must agree. */
    static void runAll() {
        var users = Repositories.users();
        same("user text = phone part '63333'", List.of("Tomas Petraitis"), names(users.search("63333", null, null)));
        same("user text = company name 'Letenė'", List.of("Rasa Jonaitė", "Tomas Petraitis"),
                names(users.search("Letenė", null, null)));
        same("user blocked = true (none in demo data)", List.of(), names(users.search(null, null, true)));
        User jonas = users.findByUsername("jonas").orElseThrow();
        jonas.setBlocked(true);
        users.update(jonas);
        same("user blocked = true after blocking jonas", List.of("Jonas Jonaitis"), names(users.search(null, null, true)));
        same("user blocked = false no longer has jonas", false,
                names(users.search(null, null, false)).contains("Jonas Jonaitis"));
        jonas.setBlocked(false); // put the demo data back
        users.update(jonas);

        var companies = Repositories.companies();
        same("company text = code part '98765'", List.of("Uodegėlė"), names(companies.search("98765", null, null)));
        same("company text = address part 'gedimino'", List.of("Letenėlė"), names(companies.search("gedimino", null, null)));
        same("company active = false", List.of(), names(companies.search(null, null, false)));
        same("company active = true", List.of("Letenėlė", "Uodegėlė"), names(companies.search(null, null, true)));

        var services = Repositories.services();
        same("service title 'kirp'", List.of("Kailio kirpimas"),
                names(services.search(null, null, null, null, "kirp", null, null, null, null)));
        same("service duration 60..120 (both ends included)",
                List.of("Šunų vedžiojimas", "Kailio kirpimas", "Priežiūra namuose"),
                names(services.search(null, null, null, null, null, 60, 120, null, null)));
        same("service location PROVIDER_PREMISES",
                List.of("Kailio kirpimas", "Veterinaro konsultacija", "Gyvūnų viešbutis"),
                names(services.search(null, null, null, null, null, null, null, ServiceLocation.PROVIDER_PREMISES, null)));
        same("service status INACTIVE", List.of(),
                names(services.search(null, null, null, null, null, null, null, null, ServiceStatus.INACTIVE)));
        same("service status ACTIVE", 5,
                services.search(null, null, null, null, null, null, null, null, ServiceStatus.ACTIVE).size());

        var pets = Repositories.pets();
        same("pet size SMALL", List.of("Murka", "Žydrukas", "Pūkas"),
                names(pets.search(null, null, null, PetSize.SMALL, null, null, null, null)));
        same("pet born 2021-01-01..2022-12-31", List.of("Murka", "Bimas"),
                names(pets.search(null, null, null, null, LocalDate.of(2021, 1, 1), LocalDate.of(2022, 12, 31), null, null)));
        same("pet born on exactly 2024-02-14 (one-day range)", List.of("Pūkas"),
                names(pets.search(null, null, null, null, LocalDate.of(2024, 2, 14), LocalDate.of(2024, 2, 14), null, null)));
        same("pet weight 4.2..14 (both ends included)", List.of("Murka", "Bimas"),
                names(pets.search(null, null, null, null, null, null, 4.2, 14.0)));

        var reservations = Repositories.reservations();
        same("reservation text = pet 'Reks'", List.of("Šunų vedžiojimas – Reksas"),
                bookings(reservations.search(null, null, null, null, "Reks", null, null)));
        same("reservation text = service 'kirp'", List.of("Kailio kirpimas – Bimas"),
                bookings(reservations.search(null, null, null, null, "kirp", null, null)));
        same("reservation text = caretaker 'Eglė'",
                List.of("Veterinaro konsultacija – Pūkas", "Gyvūnų viešbutis – Murka"),
                bookings(reservations.search(null, null, null, null, "Eglė", null, null)));
        same("reservation price 25..30", List.of("Gyvūnų viešbutis – Murka", "Priežiūra namuose – Žydrukas"),
                bookings(reservations.search(null, null, null, null, null, new BigDecimal("25"), new BigDecimal("30"))));
        same("reservation text '%' finds nothing (escaped, not a wildcard)", List.of(),
                bookings(reservations.search(null, null, null, null, "%", null, null)));
    }

    static List<String> names(List<?> items) {
        return items.stream().map(Object::toString).toList();
    }

    /** Without the start date: memory dates are relative to today, the SQL demo dates may not be. */
    static List<String> bookings(List<Reservation> items) {
        return items.stream().map(r -> r.getService() + " – " + r.getPet()).toList();
    }

    static void same(String label, Object expected, Object actual) {
        boolean ok = expected.equals(actual);
        if (ok) {
            pass++;
        } else {
            fail++;
        }
        System.out.println((ok ? "PASS " : "FAIL ") + label + (ok ? "" : " | expected=" + expected + " actual=" + actual));
    }
}
