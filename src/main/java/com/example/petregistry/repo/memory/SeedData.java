package com.example.petregistry.repo.memory;

import com.example.petregistry.model.Caretaker;
import com.example.petregistry.model.Company;
import com.example.petregistry.model.CompanyManager;
import com.example.petregistry.model.Pet;
import com.example.petregistry.model.PetOwner;
import com.example.petregistry.model.PetSize;
import com.example.petregistry.model.Rateable;
import com.example.petregistry.model.Reservation;
import com.example.petregistry.model.Review;
import com.example.petregistry.model.Service;
import com.example.petregistry.model.ServiceLocation;
import com.example.petregistry.model.ServiceType;
import com.example.petregistry.model.Species;
import com.example.petregistry.model.User;
import com.example.petregistry.repo.CompanyRepository;
import com.example.petregistry.repo.PetRepository;
import com.example.petregistry.repo.ReservationRepository;
import com.example.petregistry.repo.ReviewRepository;
import com.example.petregistry.repo.ServiceRepository;
import com.example.petregistry.repo.UserRepository;
import org.mindrot.jbcrypt.BCrypt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * Demo data so every tab has rows: 2 companies, 1 manager each, 3 caretakers, 3 owners, 5 pets, 5 services,
 * 5 reservations in different statuses, 2 reviews. Every user's password is "Slaptazodis1".
 * Average ratings stay 0 here: only ReviewService.refreshAverage sets them.
 */
public final class SeedData {

    private SeedData() {
    }

    public static void fill(UserRepository users, CompanyRepository companies, ServiceRepository services,
                            PetRepository pets, ReservationRepository reservations, ReviewRepository reviews) {
        // Hashing is slow on purpose (that is what makes BCrypt safe), so hash once and reuse it.
        String hash = BCrypt.hashpw("Slaptazodis1", BCrypt.gensalt());

        // ---- Companies ----
        Company letenele = companies.create(company("Letenėlė", "123456789", "Vilnius", "Gedimino pr. 1"));
        Company uodegele = companies.create(company("Uodegėlė", "987654321", "Kaunas", "Laisvės al. 10"));

        // ---- Managers: 1 per company ----
        CompanyManager rasa = new CompanyManager();
        fillUser(rasa, "rasa", "Rasa", "Jonaitė", "rasa@letenele.lt", "+37061111111", hash);
        rasa.setCompany(letenele);
        users.create(rasa);

        CompanyManager mantas = new CompanyManager();
        fillUser(mantas, "mantas", "Mantas", "Kazlauskas", "mantas@uodegele.lt", "+37062222222", hash);
        mantas.setCompany(uodegele);
        users.create(mantas);

        // ---- Caretakers: 2 hired, 1 independent ----
        Caretaker tomas = caretaker("tomas", "Tomas", "Petraitis", "tomas@letenele.lt", "+37063333333",
                hash, "Myliu šunis, vedžioju kasdien.", 4);
        users.create(tomas);
        letenele.hireCaretaker(tomas);

        Caretaker egle = caretaker("egle", "Eglė", "Vaitkutė", "egle@uodegele.lt", "+37064444444",
                hash, "Veterinarijos studentė.", 2);
        users.create(egle);
        uodegele.hireCaretaker(egle);

        Caretaker lukas = caretaker("lukas", "Lukas", "Stankevičius", "lukas@gmail.com", "",
                hash, "Dirbu savarankiškai.", 7);
        users.create(lukas); // independent: no company

        // ---- Owners ----
        PetOwner anna = owner("anna", "Anna", "Kazlauskienė", "anna@gmail.com", "+37065555555",
                hash, "Vilnius, Žirmūnų g. 5");
        users.create(anna);
        PetOwner jonas = owner("jonas", "Jonas", "Jonaitis", "jonas@gmail.com", "+37066666666",
                hash, "Kaunas, Savanorių pr. 20");
        users.create(jonas);
        PetOwner greta = owner("greta", "Greta", "Paulauskaitė", "greta@gmail.com", "",
                hash, "Vilnius, Antakalnio g. 12");
        users.create(greta);

        // ---- Pets: create first (gets an id), then link with addPet so both sides are in sync ----
        Pet reksas = pets.create(pet("Reksas", Species.DOG, PetSize.LARGE, LocalDate.of(2019, 4, 12), 32.5));
        anna.addPet(reksas);
        Pet murka = pets.create(pet("Murka", Species.CAT, PetSize.SMALL, LocalDate.of(2021, 8, 3), 4.2));
        anna.addPet(murka);
        Pet bimas = pets.create(pet("Bimas", Species.DOG, PetSize.MEDIUM, LocalDate.of(2022, 1, 20), 14.0));
        jonas.addPet(bimas);
        Pet zydrukas = pets.create(pet("Žydrukas", Species.BIRD, PetSize.SMALL, LocalDate.of(2023, 6, 1), 0.1));
        jonas.addPet(zydrukas);
        Pet pukas = pets.create(pet("Pūkas", Species.RODENT, PetSize.SMALL, LocalDate.of(2024, 2, 14), 0.3));
        greta.addPet(pukas);

        // ---- Services: 3 at Letenėlė, 2 at Uodegėlė ----
        Service walking = services.create(service("Šunų vedžiojimas", ServiceType.DOG_WALKING, "15.00", 60,
                ServiceLocation.OUTDOORS, EnumSet.of(Species.DOG), EnumSet.allOf(PetSize.class)));
        letenele.addService(walking);
        Service grooming = services.create(service("Kailio kirpimas", ServiceType.GROOMING, "35.00", 90,
                ServiceLocation.PROVIDER_PREMISES, EnumSet.of(Species.DOG, Species.CAT),
                EnumSet.allOf(PetSize.class)));
        letenele.addService(grooming);
        Service sitting = services.create(service("Priežiūra namuose", ServiceType.PET_SITTING, "25.00", 120,
                ServiceLocation.CLIENT_HOME, EnumSet.allOf(Species.class), EnumSet.allOf(PetSize.class)));
        letenele.addService(sitting);
        Service vet = services.create(service("Veterinaro konsultacija", ServiceType.VETERINARY, "40.00", 30,
                ServiceLocation.PROVIDER_PREMISES, EnumSet.allOf(Species.class), EnumSet.allOf(PetSize.class)));
        uodegele.addService(vet);
        Service boarding = services.create(service("Gyvūnų viešbutis", ServiceType.BOARDING, "30.00", 1440,
                ServiceLocation.PROVIDER_PREMISES, EnumSet.of(Species.DOG, Species.CAT),
                EnumSet.of(PetSize.SMALL, PetSize.MEDIUM)));
        uodegele.addService(boarding);

        // ---- Reservations: one per status. Dates are relative to today so "future" stays future. ----
        LocalDateTime today10 = LocalDate.now().atTime(10, 0);

        Reservation pending = reservation(anna, reksas, walking, tomas, today10.plusDays(3));
        reservations.create(pending);

        Reservation confirmed = reservation(jonas, bimas, grooming, tomas, today10.plusDays(5));
        confirmed.confirm();
        reservations.create(confirmed);

        Reservation inProgress = reservation(greta, pukas, vet, egle, today10);
        inProgress.confirm();
        inProgress.start();
        reservations.create(inProgress);

        Reservation completed = reservation(anna, murka, boarding, egle, today10.minusDays(10));
        completed.confirm();
        completed.start();
        completed.complete();
        reservations.create(completed);

        Reservation cancelled = reservation(jonas, zydrukas, sitting, tomas, today10.plusDays(7));
        cancelled.cancel();
        reservations.create(cancelled);

        // ---- Reviews: both on the COMPLETED reservation, one by each participant ----
        Review ownerReview = review(anna, egle, 5, "Murka grįžo laiminga, ačiū!", null);
        reviews.create(ownerReview);
        completed.addReview(ownerReview);

        Review caretakerReview = review(egle, anna, 4, "Maloni šeimininkė, aiškios instrukcijos.", 5);
        reviews.create(caretakerReview);
        completed.addReview(caretakerReview);
    }

    // ----------------------------------------------------------------- helpers

    private static Company company(String name, String code, String city, String address) {
        Company c = new Company();
        c.setName(name);
        c.setCompanyCode(code);
        c.setCity(city);
        c.setAddress(address);
        return c;
    }

    private static void fillUser(User u, String username, String first, String last,
                                 String email, String phone, String hash) {
        u.setUsername(username);
        u.setFirstName(first);
        u.setLastName(last);
        u.setEmail(email);
        u.setPhone(phone);
        u.setPasswordHash(hash);
    }

    private static Caretaker caretaker(String username, String first, String last, String email, String phone,
                                       String hash, String bio, int years) {
        Caretaker c = new Caretaker();
        fillUser(c, username, first, last, email, phone, hash);
        c.setBio(bio);
        c.setExperienceYears(years);
        return c;
    }

    private static PetOwner owner(String username, String first, String last, String email, String phone,
                                  String hash, String address) {
        PetOwner o = new PetOwner();
        fillUser(o, username, first, last, email, phone, hash);
        o.setAddress(address);
        return o;
    }

    private static Pet pet(String name, Species species, PetSize size, LocalDate birthDate, double weightKg) {
        Pet p = new Pet();
        p.setName(name);
        p.setSpecies(species);
        p.setSize(size);
        p.setBirthDate(birthDate);
        p.setWeightKg(weightKg);
        return p;
    }

    private static Service service(String title, ServiceType type, String price, int minutes,
                                   ServiceLocation location, Set<Species> species, Set<PetSize> sizes) {
        Service s = new Service();
        s.setTitle(title);
        s.setDescription(title);
        s.setType(type);
        s.setPrice(new BigDecimal(price)); // from a String: new BigDecimal(15.00) would carry double rounding
        s.setDurationMinutes(minutes);
        s.setLocation(location);
        s.setAllowedSpecies(species);
        s.setAllowedSizes(sizes);
        return s;
    }

    /** The same rules ReservationService will use in M4: end = start + duration, price copied from the service. */
    private static Reservation reservation(PetOwner owner, Pet pet, Service service, Caretaker caretaker,
                                           LocalDateTime start) {
        Reservation r = new Reservation();
        r.setOwner(owner);
        r.setPet(pet);
        r.setService(service);
        r.setCaretaker(caretaker);
        r.setStart(start);
        r.setEnd(start.plusMinutes(service.getDurationMinutes()));
        r.setPrice(service.getPrice());
        return r;
    }

    private static Review review(User author, Rateable target, int rating,
                                 String comment, Integer petBehaviorRating) {
        Review r = new Review();
        r.setAuthor(author);
        r.setTarget(target);
        r.setRating(rating);
        r.setComment(comment);
        r.setPetBehaviorRating(petBehaviorRating);
        return r;
    }
}
