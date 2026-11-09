import com.example.petregistry.model.*;
import com.example.petregistry.repo.*;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class M3FullCheck {
    static int passed = 0;
    static int failed = 0;

    static void check(String label, Object expected, Object actual) {
        boolean ok = String.valueOf(expected).equals(String.valueOf(actual));
        if (ok) passed++; else failed++;
        System.out.println((ok ? "PASS " : "FAIL ") + label + " | expected=" + expected + " actual=" + actual);
    }

    static <T> T byName(List<T> list, String name) {
        return list.stream().filter(x -> x.toString().equals(name)).findFirst().orElseThrow();
    }

    public static void main(String[] args) {
        // ---- Repositories before useMemory ----
        try {
            Repositories.pets();
            check("getter before useMemory throws", "IllegalStateException", "no exception");
        } catch (IllegalStateException e) {
            check("getter before useMemory throws", "IllegalStateException", "IllegalStateException");
        }

        Repositories.useMemory();
        UserRepository users = Repositories.users();
        CompanyRepository companies = Repositories.companies();
        ServiceRepository services = Repositories.services();
        PetRepository pets = Repositories.pets();
        ReservationRepository reservations = Repositories.reservations();
        ReviewRepository reviews = Repositories.reviews();

        // ---- Seed counts from BUILD_PLAN ----
        check("companies", 2, companies.findAll().size());
        check("managers", 2, users.search(null, Role.COMPANY_MANAGER).size());
        check("caretakers", 3, users.search(null, Role.CARETAKER).size());
        check("owners", 3, users.search(null, Role.PET_OWNER).size());
        check("pets", 5, pets.findAll().size());
        check("services", 5, services.findAll().size());
        check("reservations", 5, reservations.findAll().size());
        check("reviews", 2, reviews.findAll().size());
        Set<ReservationStatus> statuses = reservations.findAll().stream()
                .map(Reservation::getStatus).collect(Collectors.toSet());
        check("5 different reservation statuses", 5, statuses.size());

        Company letenele = companies.findByCode("123456789").orElseThrow();
        Company uodegele = companies.findByCode("987654321").orElseThrow();
        PetOwner anna = (PetOwner) users.findByUsername("anna").orElseThrow();
        PetOwner greta = (PetOwner) users.findByUsername("greta").orElseThrow();
        Caretaker tomas = (Caretaker) users.findByUsername("tomas").orElseThrow();
        Caretaker egle = (Caretaker) users.findByUsername("egle").orElseThrow();
        Caretaker lukas = (Caretaker) users.findByUsername("lukas").orElseThrow();
        Pet reksas = byName(pets.findAll(), "Reksas");
        Service walking = byName(services.findAll(), "Šunų vedžiojimas");
        Service vet = byName(services.findAll(), "Veterinaro konsultacija");

        // ---- Step 3: UserRepository lookups ----
        check("countManagersOf Letenėlė", 1, users.countManagersOf(letenele.getId()));
        check("countManagersOf Uodegėlė", 1, users.countManagersOf(uodegele.getId()));
        check("countManagersOf unknown", 0, users.countManagersOf(999));
        check("findByUsername 'RASA' (case-insens)", true, users.findByUsername("RASA").isPresent());
        check("findByUsername unknown", false, users.findByUsername("nobody").isPresent());
        check("findByUsername null", false, users.findByUsername(null).isPresent());
        check("findByEmail 'ANNA@GMAIL.COM'", "Anna Kazlauskienė", users.findByEmail("ANNA@GMAIL.COM").orElseThrow());
        check("findByEmail null", false, users.findByEmail(null).isPresent());
        check("findOwners", "[Anna Kazlauskienė, Jonas Jonaitis, Greta Paulauskaitė]", users.findOwners());
        check("findCaretakers(null) = all 3", 3, users.findCaretakers(null).size());
        check("findCaretakers(Letenėlė)", "[Tomas Petraitis]", users.findCaretakers(letenele.getId()));
        check("findCaretakers(Uodegėlė)", "[Eglė Vaitkutė]", users.findCaretakers(uodegele.getId()));
        check("Lukas is independent", true, lukas.isIndependent());

        // ---- Step 3: CompanyRepository lookup ----
        check("findByCode unknown", false, companies.findByCode("000000000").isPresent());
        check("findByCode null", false, companies.findByCode(null).isPresent());

        // ---- Step 3: ReservationRepository counts ----
        check("countByPet Reksas", 1, reservations.countByPet(reksas.getId()));
        check("countByOwner Anna", 2, reservations.countByOwner(anna.getId()));
        check("countByOwner Greta", 1, reservations.countByOwner(greta.getId()));
        check("countByCaretaker Tomas", 3, reservations.countByCaretaker(tomas.getId()));
        check("countByCaretaker Eglė", 2, reservations.countByCaretaker(egle.getId()));
        check("countByCaretaker Lukas", 0, reservations.countByCaretaker(lukas.getId()));
        check("countByService walking", 1, reservations.countByService(walking.getId()));
        check("countByService vet", 1, reservations.countByService(vet.getId()));
        check("countByPet unknown", 0, reservations.countByPet(999));

        // ---- Reviews ----
        Reservation completed = reservations.search(ReservationStatus.COMPLETED, null, null, null).get(0);
        check("reviews of COMPLETED reservation", 2, reviews.findByReservation(completed.getId()).size());
        check("reservation.getReviews() in sync", 2, completed.getReviews().size());
        check("reviews about Eglė", 1, reviews.findByTarget(egle).size());
        check("reviews about Anna", 1, reviews.findByTarget(anna).size());
        check("caretaker review has petBehaviorRating", 5,
                reviews.findByTarget(anna).get(0).getPetBehaviorRating());

        // ---- Seed integrity: data must obey the M4 rules, or M4 tests will be confusing ----
        boolean petsInSync = pets.findAll().stream()
                .allMatch(p -> p.getOwner() != null && p.getOwner().getPets().contains(p));
        check("every pet has an owner, both sides in sync", true, petsInSync);
        boolean servicesInSync = services.findAll().stream()
                .allMatch(s -> s.getCompany() != null && s.getCompany().getServices().contains(s));
        check("every service has a company, both sides in sync", true, servicesInSync);
        check("Letenėlė caretakers list in sync", "[Tomas Petraitis]", letenele.getCaretakers());
        boolean reservationsValid = reservations.findAll().stream().allMatch(r ->
                r.getService().accepts(r.getPet())
                        && r.getPet().getOwner().equals(r.getOwner())
                        && r.getCaretaker().getCompany().equals(r.getService().getCompany())
                        && r.getPrice().compareTo(r.getService().getPrice()) == 0
                        && r.getEnd().equals(r.getStart().plusMinutes(r.getService().getDurationMinutes())));
        check("every reservation: accepts pet, pet is owner's, caretaker works for company, price+end right",
                true, reservationsValid);
        boolean durationsOk = services.findAll().stream()
                .allMatch(s -> s.getDurationMinutes() >= 15 && s.getDurationMinutes() <= 1440
                        && s.getDurationMinutes() % 15 == 0);
        check("durations 15..1440, multiple of 15", true, durationsOk);
        boolean phonesOk = users.findAll().stream()
                .allMatch(u -> u.getPhone().isEmpty() || u.getPhone().matches("\\+370\\d{8}"));
        check("phones empty or +370 + 8 digits", true, phonesOk);
        boolean codesOk = companies.findAll().stream().allMatch(c -> c.getCompanyCode().matches("\\d{9}"));
        check("company codes are 9 digits", true, codesOk);
        Set<Integer> userIds = new HashSet<>();
        users.findAll().forEach(u -> userIds.add(u.getId()));
        check("user ids are unique", 8, userIds.size());
        check("password works", true, anna.checkPassword("Slaptazodis1"));
        check("wrong password refused", false, anna.checkPassword("blogas"));

        // ---- useMemory() resets the data ----
        Pet extra = new Pet();
        extra.setName("Extra");
        pets.create(extra);
        check("pets after adding one", 6, Repositories.pets().findAll().size());
        Repositories.useMemory();
        check("pets after useMemory() again (reset)", 5, Repositories.pets().findAll().size());

        System.out.println();
        System.out.println("TOTAL: " + passed + " passed, " + failed + " failed");
    }
}
