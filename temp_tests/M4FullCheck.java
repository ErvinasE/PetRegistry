import com.example.petregistry.model.*;
import com.example.petregistry.repo.Repositories;
import com.example.petregistry.service.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Full check for M4: every validation rule and delete rule in BUILD_PLAN.md "Rules",
 * plus boundary values (e.g. weight 200 is OK, 200.1 is not).
 */
public class M4FullCheck {
    static int passed = 0;
    static int failed = 0;

    static final UserService users = new UserService();
    static final CompanyService companies = new CompanyService();
    static final ServiceService services = new ServiceService();
    static final PetService pets = new PetService();
    static final ReservationService reservations = new ReservationService();
    static final ReviewService reviews = new ReviewService();

    static void check(String label, Object expected, Object actual) {
        boolean ok = String.valueOf(expected).equals(String.valueOf(actual));
        if (ok) passed++; else failed++;
        System.out.println((ok ? "PASS " : "FAIL ") + label + " | expected=" + expected + " actual=" + actual);
    }

    /** Runs the action. Returns the set of error field names, or "OK" if it passed validation. */
    static String errorsOf(Runnable action) {
        try {
            action.run();
            return "OK";
        } catch (ValidationException e) {
            return e.getErrors().keySet().toString();
        }
    }

    static String ruleOf(Runnable action) {
        try {
            action.run();
            return "OK";
        } catch (BusinessRuleException e) {
            return "REFUSED";
        }
    }

    // ---------------------------------------------------------------- builders
    static Pet pet(String name, Species s, PetSize size, double kg) {
        Pet p = new Pet();
        p.setName(name);
        p.setSpecies(s);
        p.setSize(size);
        p.setWeightKg(kg);
        return p;
    }

    static PetOwner owner(String username, String email) {
        PetOwner o = new PetOwner();
        o.setUsername(username);
        o.setEmail(email);
        o.setFirstName("Vardas");
        o.setLastName("Pavardė");
        return o;
    }

    static Service service(String title) {
        Service s = new Service();
        s.setTitle(title);
        s.setType(ServiceType.GROOMING);
        s.setPrice(new BigDecimal("20.00"));
        s.setDurationMinutes(45);
        s.setLocation(ServiceLocation.PROVIDER_PREMISES);
        s.setAllowedSpecies(EnumSet.of(Species.DOG, Species.CAT));
        s.setAllowedSizes(EnumSet.allOf(PetSize.class));
        return s;
    }

    static Company company(String name, String code) {
        Company c = new Company();
        c.setName(name);
        c.setCompanyCode(code);
        c.setCity("Vilnius");
        return c;
    }

    static Reservation reservation(PetOwner o, Pet p, Service s, Caretaker c, LocalDateTime start) {
        Reservation r = new Reservation();
        r.setOwner(o);
        r.setPet(p);
        r.setService(s);
        r.setCaretaker(c);
        r.setStart(start);
        return r;
    }

    static Review review(User author, Rateable target, int rating) {
        Review r = new Review();
        r.setAuthor(author);
        r.setTarget(target);
        r.setRating(rating);
        return r;
    }

    static User byUsername(String username) {
        return users.findAll().stream().filter(u -> u.getUsername().equals(username)).findFirst().orElseThrow();
    }

    public static void main(String[] args) {
        Repositories.useMemory();
        reviews.refreshAllAverages();

        PetOwner anna = (PetOwner) byUsername("anna");
        PetOwner jonas = (PetOwner) byUsername("jonas");
        Caretaker tomas = (Caretaker) byUsername("tomas");
        Caretaker egle = (Caretaker) byUsername("egle");
        Caretaker lukas = (Caretaker) byUsername("lukas");
        Company letenele = companies.search("Letenėlė", null, null).get(0);
        Company uodegele = companies.search("Uodegėlė", null, null).get(0);
        Pet reksas = pets.search("Reksas", null, null, null, null, null, null, null).get(0);
        Pet murka = pets.search("Murka", null, null, null, null, null, null, null).get(0);
        Service walking = services.search(ServiceType.DOG_WALKING, null, null, null, null, null, null, null, null).get(0);
        Service grooming = services.search(ServiceType.GROOMING, null, null, null, null, null, null, null, null).get(0);
        LocalDateTime tomorrow = LocalDateTime.now().plusDays(1).withNano(0);

        System.out.println("---- ValidationException itself ----");
        try {
            pets.create(pet("", null, null, 0), null);
        } catch (ValidationException e) {
            check("message joins all errors with new lines", 5, e.getMessage().split("\n").length);
            try {
                e.getErrors().put("x", "y");
                check("getErrors() is read-only", "UnsupportedOperationException", "no exception");
            } catch (UnsupportedOperationException ex) {
                check("getErrors() is read-only", "UnsupportedOperationException", "UnsupportedOperationException");
            }
        }
        check("averages after refreshAllAverages: Eglė", 5.0, egle.getAverageRating());
        check("averages after refreshAllAverages: Anna", 4.0, anna.getAverageRating());

        System.out.println("---- Pet rules ----");
        check("valid pet", "OK", errorsOf(() -> pets.create(pet("Ok1", Species.DOG, PetSize.SMALL, 5), anna)));
        check("blank name (spaces)", "[name]", errorsOf(() -> pets.create(pet("   ", Species.DOG, PetSize.SMALL, 5), anna)));
        check("weight 0", "[weightKg]", errorsOf(() -> pets.create(pet("W", Species.DOG, PetSize.SMALL, 0), anna)));
        check("weight 200 (boundary OK)", "OK", errorsOf(() -> pets.create(pet("W200", Species.DOG, PetSize.LARGE, 200), anna)));
        check("weight 200.1", "[weightKg]", errorsOf(() -> pets.create(pet("W", Species.DOG, PetSize.LARGE, 200.1), anna)));
        Pet future = pet("F", Species.DOG, PetSize.SMALL, 5);
        future.setBirthDate(LocalDate.now().plusDays(1));
        check("birth date tomorrow", "[birthDate]", errorsOf(() -> pets.create(future, anna)));
        Pet today = pet("Today", Species.DOG, PetSize.SMALL, 5);
        today.setBirthDate(LocalDate.now());
        check("birth date today (OK)", "OK", errorsOf(() -> pets.create(today, anna)));
        check("no owner", "[owner]", errorsOf(() -> pets.create(pet("N", Species.DOG, PetSize.SMALL, 5), null)));
        int petsBefore = pets.findAll().size();
        errorsOf(() -> pets.create(pet("", Species.DOG, PetSize.SMALL, 5), anna));
        check("invalid pet is NOT stored", petsBefore, pets.findAll().size());
        Pet mover = pets.create(pet("Mover", Species.CAT, PetSize.SMALL, 3), anna);
        pets.update(mover, jonas);
        check("update with new owner moves the pet (new owner side)", true, jonas.getPets().contains(mover));
        check("update with new owner moves the pet (old owner side)", false, anna.getPets().contains(mover));
        check("pet with reservations can't be deleted", "REFUSED", ruleOf(() -> pets.delete(reksas)));
        check("pet without reservations deleted", "OK", ruleOf(() -> pets.delete(mover)));
        check("deleted pet removed from owner", false, jonas.getPets().contains(mover));

        System.out.println("---- User rules ----");
        check("valid owner", "OK", errorsOf(() -> users.create(owner("new_user1", "n1@mail.lt"), "Slaptazodis1", "Slaptazodis1")));
        check("username 2 chars", "[username]", errorsOf(() -> users.create(owner("ab", "a@mail.lt"), "Slaptazodis1", "Slaptazodis1")));
        check("username 3 chars (boundary OK)", "OK", errorsOf(() -> users.create(owner("abc", "abc@mail.lt"), "Slaptazodis1", "Slaptazodis1")));
        check("username 31 chars", "[username]", errorsOf(() -> users.create(owner("a".repeat(31), "b@mail.lt"), "Slaptazodis1", "Slaptazodis1")));
        check("username with space/dash", "[username]", errorsOf(() -> users.create(owner("bad name-", "c@mail.lt"), "Slaptazodis1", "Slaptazodis1")));
        check("username taken (case-insensitive)", "[username]", errorsOf(() -> users.create(owner("ANNA", "d@mail.lt"), "Slaptazodis1", "Slaptazodis1")));
        check("email bad format", "[email]", errorsOf(() -> users.create(owner("user_e1", "not-an-email"), "Slaptazodis1", "Slaptazodis1")));
        check("email taken", "[email]", errorsOf(() -> users.create(owner("user_e2", "ANNA@gmail.com"), "Slaptazodis1", "Slaptazodis1")));
        PetOwner phoneBad = owner("user_p1", "p1@mail.lt");
        phoneBad.setPhone("861234567");
        check("phone without +370", "[phone]", errorsOf(() -> users.create(phoneBad, "Slaptazodis1", "Slaptazodis1")));
        PetOwner phoneShort = owner("user_p2", "p2@mail.lt");
        phoneShort.setPhone("+3706123456");
        check("phone +370 + 7 digits", "[phone]", errorsOf(() -> users.create(phoneShort, "Slaptazodis1", "Slaptazodis1")));
        PetOwner phoneOk = owner("user_p3", "p3@mail.lt");
        phoneOk.setPhone("+37061234567");
        check("phone +370 + 8 digits (OK)", "OK", errorsOf(() -> users.create(phoneOk, "Slaptazodis1", "Slaptazodis1")));
        check("password 7 chars", "[password]", errorsOf(() -> users.create(owner("user_w1", "w1@mail.lt"), "Abcdef1", "Abcdef1")));
        check("passwords differ", "[passwordRepeat]", errorsOf(() -> users.create(owner("user_w2", "w2@mail.lt"), "Slaptazodis1", "Slaptazodis2")));
        check("password missing on create", "[password, passwordRepeat]", errorsOf(() -> users.create(owner("user_w3", "w3@mail.lt"), "", "x")));
        PetOwner noNames = owner("user_nn", "nn@mail.lt");
        noNames.setFirstName(" ");
        noNames.setLastName(null);
        check("first/last name required", "[firstName, lastName]", errorsOf(() -> users.create(noNames, "Slaptazodis1", "Slaptazodis1")));
        CompanyManager noCompany = new CompanyManager();
        noCompany.setUsername("mgr_x");
        noCompany.setEmail("mgr@mail.lt");
        noCompany.setFirstName("A");
        noCompany.setLastName("B");
        check("manager without company", "[company]", errorsOf(() -> users.create(noCompany, "Slaptazodis1", "Slaptazodis1")));

        PetOwner hashed = owner("hash_user", "hash@mail.lt");
        users.create(hashed, "Slaptazodis1", "Slaptazodis1");
        check("stored value is a hash, not the password", false, "Slaptazodis1".equals(hashed.getPasswordHash()));
        check("BCrypt hash starts with $2a$", true, hashed.getPasswordHash().startsWith("$2a$"));
        String oldHash = hashed.getPasswordHash();
        hashed.setFirstName("Pakeistas");
        check("edit with blank password is OK", "OK", errorsOf(() -> users.update(hashed, "", "")));
        check("blank password on edit keeps old hash", oldHash, hashed.getPasswordHash());
        check("edit keeps own username (not 'taken' by itself)", "OK", errorsOf(() -> users.update(hashed, null, null)));
        check("edit with new short password", "[password, passwordRepeat]", errorsOf(() -> users.update(hashed, "short", "")));
        users.update(hashed, "NaujasSlapt1", "NaujasSlapt1");
        check("new password works after edit", true, hashed.checkPassword("NaujasSlapt1"));
        check("old password no longer works", false, hashed.checkPassword("Slaptazodis1"));
        hashed.setUsername("tomas");
        check("edit to someone else's username", "[username]", errorsOf(() -> users.update(hashed, null, null)));
        hashed.setUsername("hash_user");

        check("owner with reservations can't be deleted", "REFUSED", ruleOf(() -> users.delete(anna)));
        PetOwner leaving = owner("leaving", "leaving@mail.lt");
        users.create(leaving, "Slaptazodis1", "Slaptazodis1");
        Pet leftPet1 = pets.create(pet("L1", Species.DOG, PetSize.SMALL, 5), leaving);
        Pet leftPet2 = pets.create(pet("L2", Species.CAT, PetSize.SMALL, 3), leaving);
        check("owner without reservations deleted", "OK", ruleOf(() -> users.delete(leaving)));
        check("...and both their pets deleted", false,
                pets.findAll().contains(leftPet1) || pets.findAll().contains(leftPet2));
        check("caretaker with reservations can't be deleted", "REFUSED", ruleOf(() -> users.delete(tomas)));
        Caretaker hired = new Caretaker();
        hired.setUsername("hired");
        hired.setEmail("hired@mail.lt");
        hired.setFirstName("H");
        hired.setLastName("H");
        users.create(hired, "Slaptazodis1", "Slaptazodis1");
        uodegele.hireCaretaker(hired);
        check("caretaker without reservations deleted", "OK", ruleOf(() -> users.delete(hired)));
        check("...and released from the company", false, uodegele.getCaretakers().contains(hired));

        System.out.println("---- Company rules ----");
        check("valid company", "OK", errorsOf(() -> companies.create(company("Nauja", "555666777"))));
        check("code 8 digits", "[companyCode]", errorsOf(() -> companies.create(company("X", "12345678"))));
        check("code with a letter", "[companyCode]", errorsOf(() -> companies.create(company("X", "12345678A"))));
        check("code taken", "[companyCode]", errorsOf(() -> companies.create(company("X", "123456789"))));
        Company noCity = company(" ", "999888777");
        noCity.setCity("");
        check("name and city required", "[name, city]", errorsOf(() -> companies.create(noCity)));
        letenele.setName("Letenėlė UAB");
        check("edit keeps own code (not 'taken' by itself)", "OK", errorsOf(() -> companies.update(letenele)));
        letenele.setName("Letenėlė");
        check("company with a manager can't be deleted", "REFUSED", ruleOf(() -> companies.delete(letenele)));

        System.out.println("---- Service rules ----");
        check("valid service", "OK", errorsOf(() -> services.create(service("S1"), letenele)));
        Service priceNeg = service("P");
        priceNeg.setPrice(new BigDecimal("-0.01"));
        check("price -0.01", "[price]", errorsOf(() -> services.create(priceNeg, letenele)));
        Service priceMax = service("PMax");
        priceMax.setPrice(new BigDecimal("10000.00"));
        check("price 10000.00 (boundary OK)", "OK", errorsOf(() -> services.create(priceMax, letenele)));
        Service priceOver = service("P");
        priceOver.setPrice(new BigDecimal("10000.01"));
        check("price 10000.01", "[price]", errorsOf(() -> services.create(priceOver, letenele)));
        Service priceZero = service("PZero");
        priceZero.setPrice(BigDecimal.ZERO);
        check("price 0 (boundary OK)", "OK", errorsOf(() -> services.create(priceZero, letenele)));
        Service noPrice = service("P");
        noPrice.setPrice(null);
        check("price missing", "[price]", errorsOf(() -> services.create(noPrice, letenele)));
        Service d10 = service("D");
        d10.setDurationMinutes(10);
        check("duration 10", "[durationMinutes]", errorsOf(() -> services.create(d10, letenele)));
        Service d50 = service("D");
        d50.setDurationMinutes(50);
        check("duration 50 (not x15)", "[durationMinutes]", errorsOf(() -> services.create(d50, letenele)));
        Service d1440 = service("D1440");
        d1440.setDurationMinutes(1440);
        check("duration 1440 (boundary OK)", "OK", errorsOf(() -> services.create(d1440, letenele)));
        Service d1455 = service("D");
        d1455.setDurationMinutes(1455);
        check("duration 1455", "[durationMinutes]", errorsOf(() -> services.create(d1455, letenele)));
        Service noSets = service("Sets");
        noSets.setAllowedSpecies(Set.of());
        noSets.setAllowedSizes(Set.of());
        check("no species and no sizes", "[allowedSpecies, allowedSizes]", errorsOf(() -> services.create(noSets, letenele)));
        check("no company", "[company]", errorsOf(() -> services.create(service("NC"), null)));
        Service movingService = services.create(service("Moving"), letenele);
        services.update(movingService, uodegele);
        check("update with new company moves the service", "true/false",
                uodegele.getServices().contains(movingService) + "/" + letenele.getServices().contains(movingService));
        check("booked service delete -> only deactivated", false, services.delete(grooming));
        check("...status is INACTIVE", ServiceStatus.INACTIVE, grooming.getStatus());
        check("...still stored", true, services.findAll().contains(grooming));
        check("unbooked service delete -> really deleted", true, services.delete(movingService));
        check("...removed from company", false, uodegele.getServices().contains(movingService));

        System.out.println("---- Reservation rules ----");
        // Tomas works for Letenėlė; walking is Letenėlė's DOG service; Reksas is Anna's large dog.
        Reservation ok = reservation(anna, reksas, walking, tomas, tomorrow);
        check("valid reservation", "OK", errorsOf(() -> reservations.create(ok)));
        check("create sets end = start + 60 min", tomorrow.plusMinutes(60), ok.getEnd());
        check("create copies the service price", "15.00", ok.getPrice());
        walking.setPrice(new BigDecimal("99.00"));
        check("later price change does not rewrite the booking", "15.00", ok.getPrice());
        walking.setPrice(new BigDecimal("15.00"));
        check("all required missing", "[owner, pet, service, start]",
                errorsOf(() -> reservations.create(new Reservation())));
        check("pet is not the owner's", "[pet]", errorsOf(() -> reservations.create(reservation(jonas, reksas, walking, tomas, tomorrow))));
        check("service doesn't accept the pet (cat on dog walk)", "[pet]", errorsOf(() -> reservations.create(reservation(anna, murka, walking, tomas, tomorrow))));
        check("service not ACTIVE (grooming is INACTIVE now)", "[service]", errorsOf(() -> reservations.create(reservation(anna, reksas, grooming, tomas, tomorrow))));
        check("caretaker from another company", "[caretaker]", errorsOf(() -> reservations.create(reservation(anna, reksas, walking, egle, tomorrow))));
        check("independent caretaker", "[caretaker]", errorsOf(() -> reservations.create(reservation(anna, reksas, walking, lukas, tomorrow))));
        check("no caretaker is allowed (0..1)", "OK", errorsOf(() -> reservations.create(reservation(anna, reksas, walking, null, tomorrow))));
        check("start in the past", "[start]", errorsOf(() -> reservations.create(reservation(anna, reksas, walking, tomas, LocalDateTime.now().minusHours(1)))));
        Reservation completed = reservations.search(ReservationStatus.COMPLETED, null, null, null, null, null, null).get(0);
        check("COMPLETED reservation in the past can still be edited", "OK", errorsOf(() -> reservations.update(completed)));

        System.out.println("---- Review rules ----");
        Reservation pending = reservations.search(ReservationStatus.PENDING, null, null, null, null, null, null).get(0);
        check("review on a not COMPLETED reservation", "[reservation]",
                errorsOf(() -> reviews.create(review(pending.getOwner(), pending.getCaretaker(), 5), pending)));
        check("author not a participant", "[author]", errorsOf(() -> reviews.create(review(jonas, egle, 5), completed)));
        check("second review by the same author", "[author]", errorsOf(() -> reviews.create(review(anna, egle, 3), completed)));
        check("rating 0", "[rating]", errorsOf(() -> reviews.update(withRating(completed, 0))));
        check("rating 6", "[rating]", errorsOf(() -> reviews.update(withRating(completed, 6))));
        Review behaviorBad = completed.getReviews().stream().filter(r -> r.getAuthor().equals(egle)).findFirst().orElseThrow();
        behaviorBad.setPetBehaviorRating(6);
        check("pet behavior rating 6", "[petBehaviorRating]", errorsOf(() -> reviews.update(behaviorBad)));
        behaviorBad.setPetBehaviorRating(5);
        Rateable oldTarget = behaviorBad.getTarget();
        behaviorBad.setTarget(null);
        check("review without target", "[target]", errorsOf(() -> reviews.update(behaviorBad)));
        behaviorBad.setTarget(oldTarget);

        System.out.println("---- Averages ----");
        Review annaAboutEgle = completed.getReviews().stream().filter(r -> r.getAuthor().equals(anna)).findFirst().orElseThrow();
        annaAboutEgle.setRating(5);
        reviews.deactivate(annaAboutEgle);
        check("deactivated review doesn't count: Eglė avg", 0.0, egle.getAverageRating());
        check("...but it is still stored", true, reviews.findAll().contains(annaAboutEgle));
        annaAboutEgle.setActive(true);
        annaAboutEgle.setRating(2);
        reviews.update(annaAboutEgle);
        check("update refreshes the average: Eglė avg", 2.0, egle.getAverageRating());
        reviews.delete(annaAboutEgle);
        check("delete refreshes the average: Eglė avg", 0.0, egle.getAverageRating());
        check("...and removes it from the reservation", 1, completed.getReviews().size());
        Review again = reviews.create(review(anna, egle, 4), completed);
        check("create refreshes the average: Eglė avg", 4.0, egle.getAverageRating());
        check("after delete, the author may review again", true, again.getId() > 0);
        reservations.delete(completed);
        check("deleting the reservation deletes its reviews", 0, reviews.findByReservation(completed.getId()).size());
        check("...and refreshes averages: Eglė", 0.0, egle.getAverageRating());
        check("...and refreshes averages: Anna", 0.0, anna.getAverageRating());

        System.out.println();
        System.out.println("TOTAL: " + passed + " passed, " + failed + " failed");
    }

    /** The first review of the reservation with a new rating, for the rating checks. */
    static Review withRating(Reservation reservation, int rating) {
        Review r = reservation.getReviews().get(0);
        r.setRating(rating);
        return r;
    }
}
