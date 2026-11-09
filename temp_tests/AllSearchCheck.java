import com.example.petregistry.model.*;
import com.example.petregistry.repo.*;
import com.example.petregistry.repo.memory.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class AllSearchCheck {
    static void show(String label, Object actual) {
        System.out.println(label + " -> " + actual);
    }

    public static void main(String[] args) {
        // ---- Pet: null-name regression ----
        PetRepository pets = new InMemoryPetRepository();
        Pet noName = new Pet();
        pets.create(noName);
        Pet rex = new Pet();
        rex.setName("Rex");
        pets.create(rex);
        show("Pet name 'rex' with a null-name pet present (expect [Rex], no crash)", pets.search("rex", null, null));

        // ---- User ----
        UserRepository users = new InMemoryUserRepository();
        PetOwner anna = new PetOwner();
        anna.setUsername("anna_k");
        anna.setFirstName("Anna");
        anna.setLastName("Kazlauskienė");
        anna.setEmail("anna@mail.lt");
        users.create(anna);
        Caretaker tomas = new Caretaker();
        tomas.setUsername("tomas");
        tomas.setFirstName("Tomas");
        tomas.setLastName("Petraitis");
        tomas.setEmail("tp@care.lt");
        users.create(tomas);
        Administrator admin = new Administrator();
        admin.setUsername("admin");
        users.create(admin); // no name, no email
        show("User text null (expect 3)", users.search(null, null).size());
        show("User text 'anna' (expect [Anna Kazlauskienė])", users.search("anna", null));
        show("User text 'PETRAI' full name (expect [Tomas Petraitis])", users.search("PETRAI", null));
        show("User text 'care.lt' email (expect [Tomas Petraitis])", users.search("care.lt", null));
        show("User role CARETAKER (expect [Tomas Petraitis])", users.search(null, Role.CARETAKER));
        show("User text 'anna' + role CARETAKER (expect [])", users.search("anna", Role.CARETAKER));

        // ---- Company ----
        CompanyRepository companies = new InMemoryCompanyRepository();
        Company c1 = new Company();
        c1.setName("Letenėlė");
        c1.setCity("Vilnius");
        companies.create(c1);
        Company c2 = new Company();
        c2.setName("Uodegėlė");
        c2.setCity("Kaunas");
        companies.create(c2);
        show("Company city 'vilnius' (expect [Letenėlė])", companies.search(null, "vilnius"));
        show("Company name 'ėlė' (expect [Letenėlė, Uodegėlė])", companies.search("ėlė", null));
        show("Company name 'uode' + city 'Vilnius' (expect [])", companies.search("uode", "Vilnius"));

        // ---- Service ----
        ServiceRepository services = new InMemoryServiceRepository();
        Service walk = new Service();
        walk.setTitle("Walk");
        walk.setType(ServiceType.DOG_WALKING);
        walk.setPrice(new BigDecimal("10.00"));
        services.create(walk);
        c1.addService(walk);
        Service vet = new Service();
        vet.setTitle("Vet");
        vet.setType(ServiceType.VETERINARY);
        vet.setPrice(new BigDecimal("45.50"));
        services.create(vet);
        c2.addService(vet);
        show("Service type VETERINARY (expect [Vet])", services.search(ServiceType.VETERINARY, null, null, null));
        show("Service company c1 (expect [Walk])", services.search(null, c1.getId(), null, null));
        show("Service min 10.0 (scale differs from 10.00) (expect [Walk, Vet])", services.search(null, null, new BigDecimal("10.0"), null));
        show("Service max 10 (expect [Walk])", services.search(null, null, null, new BigDecimal("10")));
        show("Service 11..45 (expect [])", services.search(null, null, new BigDecimal("11"), new BigDecimal("45")));

        // ---- Reservation ----
        ReservationRepository reservations = new InMemoryReservationRepository();
        Reservation r1 = new Reservation();
        r1.setOwner(anna);
        r1.setService(walk);
        r1.setStart(LocalDateTime.of(2026, 12, 1, 10, 0));
        reservations.create(r1);
        Reservation r2 = new Reservation();
        r2.setService(vet);
        r2.setStart(LocalDateTime.of(2026, 12, 5, 23, 30));
        r2.confirm();
        reservations.create(r2);
        Reservation r3 = new Reservation(); // no start date
        r3.setService(vet);
        reservations.create(r3);
        show("Reservation status CONFIRMED (expect 1 = r2)", reservations.search(ReservationStatus.CONFIRMED, null, null, null).size());
        show("Reservation owner anna (expect 1 = r1)", reservations.search(null, anna.getId(), null, null).size());
        show("Reservation from 12-01 to 12-01 (inclusive day) (expect 1 = r1)", reservations.search(null, null, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 1)).size());
        show("Reservation to 12-05 (23:30 same day counts) (expect 2)", reservations.search(null, null, null, LocalDate.of(2026, 12, 5)).size());
        show("Reservation from 12-02 (expect 1 = r2, r3 has no date)", reservations.search(null, null, LocalDate.of(2026, 12, 2), null).size());

        // ---- Review ----
        ReviewRepository reviews = new InMemoryReviewRepository();
        Review rv1 = new Review();
        rv1.setTarget(tomas);
        rv1.setRating(5);
        reviews.create(rv1);
        r1.addReview(rv1);
        Review rv2 = new Review();
        rv2.setTarget(c1);
        rv2.setRating(4);
        reviews.create(rv2);
        r1.addReview(rv2);
        Review rv3 = new Review();
        rv3.setTarget(c1);
        rv3.setRating(3);
        reviews.create(rv3);
        r2.addReview(rv3);
        show("Review by reservation r1 (expect 2)", reviews.findByReservation(r1.getId()).size());
        show("Review by reservation r2 (expect 1)", reviews.findByReservation(r2.getId()).size());
        show("Review by target c1 (expect 2)", reviews.findByTarget(c1).size());
        show("Review by target tomas (expect 1)", reviews.findByTarget(tomas).size());
        // tomas has id 2, c2 has id 2: different classes must not match
        show("Review by target c2, same id as tomas (expect 0)", reviews.findByTarget(c2).size());
    }
}
