import com.example.petregistry.db.DataAccessException;
import com.example.petregistry.db.DatabaseManager;
import com.example.petregistry.model.Administrator;
import com.example.petregistry.model.Caretaker;
import com.example.petregistry.model.Company;
import com.example.petregistry.model.CompanyManager;
import com.example.petregistry.model.Pet;
import com.example.petregistry.model.PetOwner;
import com.example.petregistry.model.PetSize;
import com.example.petregistry.model.Reservation;
import com.example.petregistry.model.ReservationStatus;
import com.example.petregistry.model.Review;
import com.example.petregistry.model.Role;
import com.example.petregistry.model.Service;
import com.example.petregistry.model.ServiceLocation;
import com.example.petregistry.model.ServiceStatus;
import com.example.petregistry.model.ServiceType;
import com.example.petregistry.model.Species;
import com.example.petregistry.model.User;
import com.example.petregistry.repo.Repositories;
import com.example.petregistry.service.BusinessRuleException;
import com.example.petregistry.service.CompanyService;
import com.example.petregistry.service.PetService;
import com.example.petregistry.service.ReservationService;
import com.example.petregistry.service.ReviewService;
import com.example.petregistry.service.ServiceService;
import com.example.petregistry.service.UserService;
import com.example.petregistry.service.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

/**
 * M11 + M12 "Done when": connect errors; create, read, update, search and delete one object of each class
 * in the DB, plus links, SQL averages, delete rules, transactions and readable constraint errors.
 * Runs on pet_registry_test (a fresh copy of docs/schema.sql), never on the real pet_registry.
 */
public class M12Check {

    static final String URL = "jdbc:mysql://localhost:3306/pet_registry_test";
    static int pass;
    static int fail;

    public static void main(String[] args) {
        try {
            run();
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("CRASHED: " + t + "  (PASSED " + pass + ", FAILED " + fail + " so far)");
            System.exit(2);
        }
        System.out.println();
        System.out.println("PASSED " + pass + ", FAILED " + fail);
        System.exit(fail == 0 ? 0 : 1);
    }

    static void run() {
        UserService users = new UserService();
        CompanyService companies = new CompanyService();
        ServiceService services = new ServiceService();
        PetService pets = new PetService();
        ReservationService reservations = new ReservationService();
        ReviewService reviews = new ReviewService();

        // ============================================================ M11 connect
        title("M11: connect / disconnect");
        try {
            DatabaseManager.connect(URL, "root", "wrong-password");
            check("wrong password refused", false);
        } catch (DataAccessException e) {
            check("wrong password → '" + e.getMessage().lines().findFirst().orElse("") + "'",
                    e.getMessage().startsWith("Nepavyko prisijungti") && !DatabaseManager.isConnected());
        }
        try {
            DatabaseManager.connect("jdbc:mysql://localhost:3306/no_such_db", "root", "");
            check("missing database refused", false);
        } catch (DataAccessException e) {
            check("missing database → 'Nepavyko prisijungti: ... Unknown database'", e.getMessage().contains("Unknown database"));
        }
        try {
            Repositories.useDatabase();
            check("useDatabase() without a connection refused", false);
        } catch (IllegalStateException e) {
            check("useDatabase() without a connection → IllegalStateException", true);
        }
        DatabaseManager.connect(URL, "root", "");
        check("connect with the XAMPP defaults (root, empty password)", DatabaseManager.isConnected());
        Repositories.useDatabase();
        check("Repositories.isDatabase()", Repositories.isDatabase());
        check("schema's admin user is there", users.findAll().size() == 1 && users.findAll().get(0) instanceof Administrator
                && users.findAll().get(0).checkPassword("admin123"));

        // ============================================================ Company
        title("Company: create / read / update / search");
        Company c = new Company();
        c.setName("Letenėlė");
        c.setCompanyCode("123456789");
        c.setCity("Vilnius");
        c.setAddress("Gedimino pr. 1");
        companies.create(c);
        check("create → id from AUTO_INCREMENT", c.getId() > 0);
        Company c2 = new Company();
        c2.setName("Uodegėlė");
        c2.setCompanyCode("987654321");
        c2.setCity("Kaunas");
        companies.create(c2);
        Company read = Repositories.companies().findById(c.getId()).orElseThrow();
        check("read back: a NEW object with the same values (Lithuanian letters kept)", read != c && read.equals(c)
                && "Letenėlė".equals(read.getName()) && "123456789".equals(read.getCompanyCode()) && read.isActive());
        c.setCity("Vilnius 2");
        companies.update(c);
        check("update → city saved", "Vilnius 2".equals(Repositories.companies().findById(c.getId()).orElseThrow().getCity()));
        check("search name 'lete' (case-insensitive LIKE) → Letenėlė", companies.search("lete", null).equals(List.of(c)));
        check("search city 'kaun' → Uodegėlė", companies.search(null, "kaun").equals(List.of(c2)));
        check("search both empty → both", companies.search("", " ").size() == 2);
        check("findByCode", Repositories.companies().findByCode("987654321").orElseThrow().equals(c2));
        try {
            Company dup = new Company();
            dup.setName("X");
            dup.setCompanyCode("123456789");
            dup.setCity("X");
            companies.create(dup);
            check("duplicate code refused", false);
        } catch (ValidationException e) {
            check("duplicate code → service message (findByCode in SQL)", e.getErrors().containsKey("companyCode"));
        }

        // ============================================================ Users
        title("Users: one of each role");
        CompanyManager rasa = new CompanyManager();
        fill(rasa, "rasa", "Rasa", "Jonaitė");
        rasa.setCompany(c);
        users.create(rasa, "Slaptazodis1", "Slaptazodis1");
        Caretaker tomas = new Caretaker();
        fill(tomas, "tomas", "Tomas", "Petraitis");
        tomas.setBio("Myliu šunis");
        tomas.setExperienceYears(4);
        users.create(tomas, "Slaptazodis1", "Slaptazodis1");
        companies.hireCaretaker(c, tomas);
        Caretaker lukas = new Caretaker();
        fill(lukas, "lukas", "Lukas", "Stankevičius");
        users.create(lukas, "Slaptazodis1", "Slaptazodis1");
        PetOwner anna = new PetOwner();
        fill(anna, "anna", "Anna", "Kazlauskienė");
        anna.setAddress("Vilnius");
        users.create(anna, "Slaptazodis1", "Slaptazodis1");
        check("4 users created (+ admin = 5)", users.findAll().size() == 5);

        User rasaDb = Repositories.users().findById(rasa.getId()).orElseThrow();
        check("role column → the right subclass (CompanyManager) with its company", rasaDb instanceof CompanyManager m
                && m.getCompany().equals(c) && "Letenėlė".equals(m.getCompany().getName()));
        Caretaker tomasDb = (Caretaker) Repositories.users().findById(tomas.getId()).orElseThrow();
        check("caretaker: bio, years, company (via hireCaretaker, both sides)", "Myliu šunis".equals(tomasDb.getBio())
                && tomasDb.getExperienceYears() == 4 && tomasDb.getCompany().equals(c)
                && tomasDb.getCompany().getCaretakers().contains(tomasDb));
        check("independent caretaker has company NULL", ((Caretaker) Repositories.users().findById(lukas.getId()).orElseThrow()).isIndependent());
        check("password hash stored, checkPassword works on the reloaded user", rasaDb.checkPassword("Slaptazodis1"));
        check("search text 'kazl' → Anna", users.search("kazl", null).equals(List.of(anna)));
        check("search role CARETAKER → Tomas, Lukas", users.search(null, Role.CARETAKER).equals(List.of(tomas, lukas)));
        check("search \"' OR 1=1 --\" finds nothing (PreparedStatement: it's just text)", users.search("' OR 1=1 --", null).isEmpty());
        check("search '_' is not a wildcard (escaped)", users.search("_", null).isEmpty());
        check("findByUsername is case-insensitive ('TOMAS')", Repositories.users().findByUsername("TOMAS").orElseThrow().equals(tomas));
        check("findByEmail", Repositories.users().findByEmail("anna@test.lt").orElseThrow().equals(anna));
        check("countManagersOf(Letenėlė) = 1", Repositories.users().countManagersOf(c.getId()) == 1);
        check("findOwners → Anna", users.findOwners().equals(List.of(anna)));
        check("findCaretakers(Letenėlė) → Tomas; (null) → both", users.findCaretakers(c.getId()).equals(List.of(tomas))
                && users.findCaretakers(null).size() == 2);
        anna.setPhone("+37061234567");
        users.update(anna, "", "");
        check("update without password keeps the hash; phone saved", Repositories.users().findById(anna.getId())
                .orElseThrow().checkPassword("Slaptazodis1") && "+37061234567".equals(Repositories.users().findById(anna.getId()).orElseThrow().getPhone()));
        users.setBlocked(lukas, true);
        check("blocked saved", Repositories.users().findById(lukas.getId()).orElseThrow().isBlocked());
        check("company now lists its caretaker (Company.caretakers filled by a query)",
                Repositories.companies().findById(c.getId()).orElseThrow().getCaretakers().equals(List.of(tomas)));

        // ============================================================ Service
        title("Service: create with species / sizes (transaction)");
        Service walk = service("Šunų vedžiojimas", ServiceType.DOG_WALKING, "15.00", 60, EnumSet.of(Species.DOG));
        services.create(walk, c);
        Service vet = service("Veterinaras", ServiceType.VETERINARY, "40.00", 30, EnumSet.allOf(Species.class));
        services.create(vet, c2);
        Service walkDb = Repositories.services().findById(walk.getId()).orElseThrow();
        check("read back: price as BigDecimal, enums, company", walkDb.getPrice().compareTo(new BigDecimal("15.00")) == 0
                && walkDb.getType() == ServiceType.DOG_WALKING && walkDb.getLocation() == ServiceLocation.OUTDOORS
                && walkDb.getStatus() == ServiceStatus.ACTIVE && walkDb.getCompany().equals(c));
        check("allowedSpecies / allowedSizes from service_species / service_sizes", walkDb.getAllowedSpecies().equals(EnumSet.of(Species.DOG))
                && walkDb.getAllowedSizes().equals(EnumSet.allOf(PetSize.class)));
        walk.setAllowedSpecies(EnumSet.of(Species.DOG, Species.CAT));
        walk.setAllowedSizes(EnumSet.of(PetSize.LARGE));
        services.update(walk, c);
        Service walk2 = Repositories.services().findById(walk.getId()).orElseThrow();
        check("update: sets replaced (delete + reinsert)", walk2.getAllowedSpecies().equals(EnumSet.of(Species.DOG, Species.CAT))
                && walk2.getAllowedSizes().equals(EnumSet.of(PetSize.LARGE)));
        check("company.services filled by a query", Repositories.companies().findById(c.getId()).orElseThrow().getServices().equals(List.of(walk)));
        check("search type VETERINARY → vet", services.search(ServiceType.VETERINARY, null, null, null).equals(List.of(vet)));
        check("search price 20–50 → vet", services.search(null, null, new BigDecimal("20"), new BigDecimal("50")).equals(List.of(vet)));
        check("search company Letenėlė → walk", services.search(null, c.getId(), null, null).equals(List.of(walk)));
        check("search price 15–15 (inclusive) → walk", services.search(null, null, new BigDecimal("15"), new BigDecimal("15.00")).equals(List.of(walk)));
        services.setTemporarilyUnavailable(vet, true);
        check("pause saved", Repositories.services().findById(vet.getId()).orElseThrow().getStatus() == ServiceStatus.TEMPORARILY_UNAVAILABLE);
        services.setTemporarilyUnavailable(vet, false);
        check("findActive → both", services.findActive().size() == 2);

        // ============================================================ Pet
        title("Pet: create with owner (composition)");
        Pet reksas = pet("Reksas", Species.DOG, PetSize.LARGE, 32.5);
        reksas.setBirthDate(LocalDate.of(2019, 4, 12));
        pets.create(reksas, anna);
        Pet murka = pet("Murka", Species.CAT, PetSize.SMALL, 4.2);
        pets.create(murka, anna);
        Pet reksasDb = Repositories.pets().findById(reksas.getId()).orElseThrow();
        check("read back: owner linked (addPet: both sides), date, weight", reksasDb.getOwner().equals(anna)
                && reksasDb.getOwner().getPets().contains(reksasDb) && reksasDb.getBirthDate().equals(LocalDate.of(2019, 4, 12))
                && reksasDb.getWeightKg() == 32.5 && reksasDb.getBirthDate() != null);
        check("Murka: birth date NULL → null", Repositories.pets().findById(murka.getId()).orElseThrow().getBirthDate() == null);
        PetOwner annaDb = (PetOwner) Repositories.users().findById(anna.getId()).orElseThrow();
        check("owner.pets filled by a query → Reksas, Murka", annaDb.getPets().equals(List.of(reksas, murka)));
        murka.setNotes("Mėgsta miegoti");
        pets.update(murka, anna);
        check("update notes", "Mėgsta miegoti".equals(Repositories.pets().findById(murka.getId()).orElseThrow().getNotes()));
        check("search species CAT → Murka", pets.search(null, Species.CAT, null).equals(List.of(murka)));
        check("search owner Anna + name 'rek' → Reksas", pets.search("rek", null, anna.getId()).equals(List.of(reksas)));

        // ============================================================ Reservation
        title("Reservation: create, status, search, counts");
        Reservation r = new Reservation();
        r.setOwner(anna);
        r.setPet(reksas);
        r.setService(walk);
        r.setCaretaker(tomas);
        LocalDateTime start = LocalDate.now().plusDays(3).atTime(10, 0);
        r.setStart(start);
        reservations.create(r);
        Reservation rDb = Repositories.reservations().findById(r.getId()).orElseThrow();
        check("read back: owner, pet, service, caretaker, start/end (start_at/end_at), price", rDb.getOwner().equals(anna)
                && rDb.getPet().equals(reksas) && rDb.getService().equals(walk) && rDb.getCaretaker().equals(tomas)
                && rDb.getStart().equals(start) && rDb.getEnd().equals(start.plusMinutes(60))
                && rDb.getPrice().compareTo(new BigDecimal("15.00")) == 0 && rDb.getStatus() == ReservationStatus.PENDING);
        Reservation noCaretaker = new Reservation();
        noCaretaker.setOwner(anna);
        noCaretaker.setPet(murka);
        noCaretaker.setService(vet);
        noCaretaker.setStart(LocalDate.now().plusDays(10).atTime(9, 0));
        reservations.create(noCaretaker);
        check("caretaker NULL → null", Repositories.reservations().findById(noCaretaker.getId()).orElseThrow().getCaretaker() == null);
        r.setStart(start.plusHours(2));
        reservations.update(r);
        check("update start → end follows", Repositories.reservations().findById(r.getId()).orElseThrow().getEnd()
                .equals(start.plusHours(2).plusMinutes(60)));
        reservations.changeStatus(r, ReservationStatus.CONFIRMED);
        reservations.changeStatus(r, ReservationStatus.IN_PROGRESS);
        reservations.changeStatus(r, ReservationStatus.COMPLETED);
        check("status changes saved → COMPLETED", Repositories.reservations().findById(r.getId()).orElseThrow().getStatus() == ReservationStatus.COMPLETED);
        check("search status COMPLETED → r", reservations.search(ReservationStatus.COMPLETED, null, null, null).equals(List.of(r)));
        check("search from = to = the start day (both inclusive) → r",
                reservations.search(null, null, start.toLocalDate(), start.toLocalDate()).equals(List.of(r)));
        check("search from day+1 → only the other one",
                reservations.search(null, anna.getId(), start.toLocalDate().plusDays(1), null).equals(List.of(noCaretaker)));
        check("countByPet / Owner / Caretaker / Service (COUNT(*))", Repositories.reservations().countByPet(reksas.getId()) == 1
                && Repositories.reservations().countByOwner(anna.getId()) == 2
                && Repositories.reservations().countByCaretaker(tomas.getId()) == 1
                && Repositories.reservations().countByService(walk.getId()) == 1);

        // ============================================================ Review
        title("Review: create, averages by SQL AVG, one per author");
        Reservation completed = Repositories.reservations().findById(r.getId()).orElseThrow(); // a fresh copy, like the GUI has
        Review byOwner = new Review();
        byOwner.setAuthor(completed.getOwner());
        byOwner.setTarget(completed.getCaretaker());
        byOwner.setRating(5);
        byOwner.setComment("Puiku!");
        reviews.create(byOwner, completed);
        Review byCaretaker = new Review();
        byCaretaker.setAuthor(completed.getCaretaker());
        byCaretaker.setTarget(completed.getOwner());
        byCaretaker.setRating(3);
        byCaretaker.setPetBehaviorRating(4);
        reviews.create(byCaretaker, completed);
        Reservation withReviews = Repositories.reservations().findById(r.getId()).orElseThrow();
        check("reservation.reviews filled by a query (2), linked back", withReviews.getReviews().size() == 2
                && withReviews.getReviews().get(0).getReservation() == withReviews);
        Review rv = withReviews.getReviews().get(1);
        check("review read back: author, target (target_user_id), behaviour", rv.getAuthor().equals(tomas)
                && rv.getTarget().equals(anna) && rv.getPetBehaviorRating() == 4 && rv.getRating() == 3);
        check("Tomas's average from SQL AVG = 5.0", ((Caretaker) Repositories.users().findById(tomas.getId()).orElseThrow()).getAverageRating() == 5.0);
        check("Anna's average from SQL AVG = 3.0", ((PetOwner) Repositories.users().findById(anna.getId()).orElseThrow()).getAverageRating() == 3.0);
        try {
            Review again = new Review();
            again.setAuthor(completed.getOwner());
            again.setTarget(completed.getService().getCompany());
            again.setRating(4);
            reviews.create(again, completed);
            check("second review by the owner refused", false);
        } catch (ValidationException e) {
            check("second review by the owner → 'jau paliko'", e.getErrors().get("author").contains("jau paliko"));
        }
        Review toCompany = new Review();
        toCompany.setAuthor(anna);
        toCompany.setTarget(c);
        toCompany.setRating(4);
        check("findByTarget(company) empty before", Repositories.reviews().findByTarget(c).isEmpty());
        reviews.deactivate(rv);
        check("deactivate → Anna's SQL average 0 (only ACTIVE reviews count)",
                ((PetOwner) Repositories.users().findById(anna.getId()).orElseThrow()).getAverageRating() == 0.0
                        && !Repositories.reviews().findById(rv.getId()).orElseThrow().isActive());
        check("findByTarget(Tomas) → 1", Repositories.reviews().findByTarget(tomas).size() == 1);

        // ============================================================ delete rules + transactions
        title("Delete rules");
        try {
            pets.delete(reksas);
            check("pet with reservations refused", false);
        } catch (BusinessRuleException e) {
            check("pet with reservations → refused (COUNT in SQL)", Repositories.pets().findById(reksas.getId()).isPresent());
        }
        check("booked service → INACTIVE, not deleted", !services.delete(walk)
                && Repositories.services().findById(walk.getId()).orElseThrow().getStatus() == ServiceStatus.INACTIVE);
        try {
            companies.delete(c);
            check("company with a manager refused", false);
        } catch (BusinessRuleException e) {
            check("company with a manager → refused", true);
        }

        title("Transactions");
        Pet pukas = pet("Pūkas", Species.RODENT, PetSize.SMALL, 0.3);
        pets.create(pukas, anna); // no reservations, so nothing blocks deleting it
        int petsBefore = Repositories.pets().findAll().size();
        try {
            Repositories.inTransaction(() -> {
                Repositories.pets().delete(pukas.getId());
                throw new IllegalStateException("boom"); // something fails halfway
            });
        } catch (IllegalStateException e) {
            // expected
        }
        check("error inside a transaction → rollback: Pūkas is still there", Repositories.pets().findAll().size() == petsBefore
                && Repositories.pets().findById(pukas.getId()).isPresent());
        try {
            Repositories.inTransaction(() -> {
                Repositories.reviews().delete(byOwner.getId());
                Repositories.reservations().delete(noCaretaker.getId());
                Repositories.users().delete(anna.getId()); // FK: anna still has pets and a reservation → fails
            });
            check("FK-blocked delete fails", false);
        } catch (DataAccessException e) {
            check("FK-blocked delete → readable message '" + e.getMessage() + "'", e.getMessage().contains("naudoja kiti įrašai"));
        }
        check("…and the 2 deletes before it were rolled back", Repositories.reviews().findById(byOwner.getId()).isPresent()
                && Repositories.reservations().findById(noCaretaker.getId()).isPresent());

        title("raw constraint errors → readable messages");
        try {
            PetOwner dup = new PetOwner();
            fill(dup, "anna", "Kita", "Anna");
            dup.setEmail("kita@test.lt");
            dup.setPasswordHash("x");
            Repositories.users().create(dup); // repository directly: no service validation
            check("duplicate username refused by UNIQUE", false);
        } catch (DataAccessException e) {
            check("duplicate username (UNIQUE, error 1062) → '" + e.getMessage() + "'", e.getMessage().startsWith("Toks įrašas jau yra"));
        }

        title("delete of each class");
        reservations.delete(withReviews);
        check("reservation deleted with its reviews (one transaction)", Repositories.reservations().findById(r.getId()).isEmpty()
                && Repositories.reviews().findByReservation(r.getId()).isEmpty());
        reservations.delete(Repositories.reservations().findById(noCaretaker.getId()).orElseThrow());
        Review lonely = new Review();
        check("no reviews left", Repositories.reviews().findAll().isEmpty() && lonely.isNew());
        pets.delete(Repositories.pets().findById(murka.getId()).orElseThrow());
        check("pet deleted", Repositories.pets().findById(murka.getId()).isEmpty());
        check("service without reservations really deleted", services.delete(Repositories.services().findById(vet.getId()).orElseThrow())
                && Repositories.services().findById(vet.getId()).isEmpty());
        users.delete(Repositories.users().findById(anna.getId()).orElseThrow());
        check("owner deleted together with her pets", Repositories.users().findById(anna.getId()).isEmpty()
                && Repositories.pets().findById(reksas.getId()).isEmpty());
        users.delete(Repositories.users().findById(rasa.getId()).orElseThrow());
        companies.delete(Repositories.companies().findById(c.getId()).orElseThrow());
        check("company deleted with its services; its caretaker stays, now independent",
                Repositories.companies().findById(c.getId()).isEmpty() && Repositories.services().findById(walk.getId()).isEmpty()
                        && ((Caretaker) Repositories.users().findById(tomas.getId()).orElseThrow()).isIndependent());
        users.delete(Repositories.users().findById(tomas.getId()).orElseThrow());
        check("caretaker deleted", Repositories.users().findById(tomas.getId()).isEmpty());

        title("data survives a reconnect");
        Company kept = new Company();
        kept.setName("Išlieka");
        kept.setCompanyCode("111222333");
        kept.setCity("Klaipėda");
        companies.create(kept);
        DatabaseManager.disconnect();
        Repositories.useMemory();
        check("after disconnect: memory mode, the company is not in memory", !Repositories.isDatabase()
                && Repositories.companies().findByCode("111222333").isEmpty());
        DatabaseManager.connect(URL, "root", "");
        Repositories.useDatabase();
        check("after reconnect: the company is still in the DB", "Išlieka".equals(
                Repositories.companies().findByCode("111222333").orElseThrow().getName()));
        DatabaseManager.disconnect();
        DatabaseManager.disconnect();
        check("disconnect twice is safe", !DatabaseManager.isConnected());
    }

    // ----------------------------------------------------------------- helpers

    static void fill(User u, String username, String first, String last) {
        u.setUsername(username);
        u.setFirstName(first);
        u.setLastName(last);
        u.setEmail(username + "@test.lt");
    }

    static Service service(String title, ServiceType type, String price, int minutes, EnumSet<Species> species) {
        Service s = new Service();
        s.setTitle(title);
        s.setDescription(title);
        s.setType(type);
        s.setPrice(new BigDecimal(price));
        s.setDurationMinutes(minutes);
        s.setLocation(type == ServiceType.DOG_WALKING ? ServiceLocation.OUTDOORS : ServiceLocation.PROVIDER_PREMISES);
        s.setAllowedSpecies(species);
        s.setAllowedSizes(EnumSet.allOf(PetSize.class));
        return s;
    }

    static Pet pet(String name, Species species, PetSize size, double weight) {
        Pet p = new Pet();
        p.setName(name);
        p.setSpecies(species);
        p.setSize(size);
        p.setWeightKg(weight);
        return p;
    }

    static void title(String text) {
        System.out.println();
        System.out.println("== " + text + " ==");
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
