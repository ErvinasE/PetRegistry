import com.example.petregistry.db.DatabaseManager;
import com.example.petregistry.model.Caretaker;
import com.example.petregistry.model.CompanyManager;
import com.example.petregistry.model.Rateable;
import com.example.petregistry.model.Reservation;
import com.example.petregistry.model.User;
import com.example.petregistry.repo.Repositories;
import com.example.petregistry.service.ReservationService;
import com.example.petregistry.service.ReviewService;

import java.util.List;
import java.util.Objects;

/**
 * The demo block in docs/schema.sql must show the SAME data as SeedData (memory mode).
 * Usage: DemoDataCheck [database name]   (default pet_registry_test, loaded WITH the demo block)
 */
public class DemoDataCheck {

    static int pass;
    static int fail;

    public static void main(String[] args) {
        String db = args.length > 0 ? args[0] : "pet_registry_test";

        Repositories.useMemory();
        new ReviewService().refreshAllAverages();
        Snapshot memory = new Snapshot();

        DatabaseManager.connect("jdbc:mysql://localhost:3306/" + db, "root", "");
        Repositories.useDatabase();
        Snapshot database = new Snapshot();

        System.out.println("== memory seed vs " + db + " demo data ==");
        same("users (role, company, average)", memory.users, database.users);
        same("companies (code, city, services, caretakers, average)", memory.companies, database.companies);
        same("services (type, price, minutes, species, sizes, company)", memory.services, database.services);
        same("pets (species, size, birth date, weight, owner)", memory.pets, database.pets);
        same("reservations (status, people, start, end, price)", memory.reservations, database.reservations);
        same("reviews (author → target, ratings, comment)", memory.reviews, database.reviews);

        System.out.println("== the demo data passes the app's own rules ==");
        for (Reservation r : Repositories.reservations().findAll()) {
            try {
                new ReservationService().validate(r);
                check("reservation " + r.getId() + " (" + r.getStatus() + ") is valid", true);
            } catch (RuntimeException e) {
                check("reservation " + r.getId() + " (" + r.getStatus() + ") is valid: " + e.getMessage(), false);
            }
        }
        check("every demo user logs in with 'Slaptazodis1', admin with 'admin123'", Repositories.users().findAll().stream()
                .allMatch(u -> u.checkPassword(u.getUsername().equals("admin") ? "admin123" : "Slaptazodis1")));
        DatabaseManager.disconnect();

        System.out.println();
        System.out.println("PASSED " + pass + ", FAILED " + fail);
        System.exit(fail == 0 ? 0 : 1);
    }

    /** Everything a tab would show, as text lines, read through the repositories. */
    static class Snapshot {
        final List<String> users = Repositories.users().findAll().stream()
                .filter(u -> !u.getUsername().equals("admin")) // only the DB has the admin
                .map(u -> u.getUsername() + " " + u.getRole() + " " + u.getFullName() + " " + u.getEmail()
                        + " phone=" + blankToNull(u.getPhone()) + " company=" + companyOf(u) + " avg=" + avg(u))
                .toList();
        final List<String> companies = Repositories.companies().findAll().stream()
                .map(c -> c.getName() + " " + c.getCompanyCode() + " " + c.getCity() + " " + c.getAddress()
                        + " services=" + c.getServices() + " caretakers=" + c.getCaretakers() + " avg=" + c.getAverageRating())
                .toList();
        final List<String> services = Repositories.services().findAll().stream()
                .map(s -> s.getTitle() + " " + s.getType() + " " + s.getPrice() + " " + s.getDurationMinutes() + " "
                        + s.getLocation() + " " + s.getStatus() + " " + s.getAllowedSpecies() + " " + s.getAllowedSizes()
                        + " " + s.getCompany())
                .toList();
        final List<String> pets = Repositories.pets().findAll().stream()
                .map(p -> p.getName() + " " + p.getSpecies() + " " + p.getSize() + " " + p.getBirthDate() + " "
                        + p.getWeightKg() + " owner=" + p.getOwner().getUsername())
                .toList();
        final List<String> reservations = Repositories.reservations().findAll().stream()
                .map(r -> r.getStatus() + " " + r.getOwner().getUsername() + " " + r.getPet() + " " + r.getService()
                        + " " + (r.getCaretaker() == null ? null : r.getCaretaker().getUsername()) + " " + r.getStart()
                        + " " + r.getEnd() + " " + r.getPrice() + " reviews=" + r.getReviews().size())
                .toList();
        final List<String> reviews = Repositories.reviews().findAll().stream()
                .map(v -> v.getAuthor().getUsername() + " → " + v.getTarget() + " " + v.getRating() + " "
                        + v.getPetBehaviorRating() + " " + v.getComment() + " active=" + v.isActive())
                .toList();
    }

    static String companyOf(User u) {
        if (u instanceof CompanyManager m) {
            return Objects.toString(m.getCompany());
        }
        if (u instanceof Caretaker c) {
            return Objects.toString(c.getCompany());
        }
        return "-";
    }

    static String avg(User u) {
        return u instanceof Rateable r ? String.valueOf(r.getAverageRating()) : "-";
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    static void same(String what, List<String> memory, List<String> database) {
        boolean ok = memory.equals(database);
        check(what + ": " + memory.size() + " rows", ok);
        if (!ok) {
            for (int i = 0; i < Math.max(memory.size(), database.size()); i++) {
                String m = i < memory.size() ? memory.get(i) : "(none)";
                String d = i < database.size() ? database.get(i) : "(none)";
                if (!m.equals(d)) {
                    System.out.println("      memory: " + m);
                    System.out.println("      db:     " + d);
                }
            }
        }
    }

    static void check(String name, boolean ok) {
        if (ok) {
            pass++;
            System.out.println("  PASS  " + name);
        } else {
            fail++;
            System.out.println("  FAIL  " + name);
        }
    }
}
