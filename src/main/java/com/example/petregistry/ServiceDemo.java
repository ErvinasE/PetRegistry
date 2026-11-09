package com.example.petregistry;

import com.example.petregistry.model.Caretaker;
import com.example.petregistry.model.Company;
import com.example.petregistry.model.CompanyManager;
import com.example.petregistry.model.Pet;
import com.example.petregistry.model.PetOwner;
import com.example.petregistry.model.PetSize;
import com.example.petregistry.model.Reservation;
import com.example.petregistry.model.ReservationStatus;
import com.example.petregistry.model.Role;
import com.example.petregistry.model.Service;
import com.example.petregistry.model.ServiceLocation;
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
import java.util.EnumSet;

/** Throwaway check for M4 ("Done when" in BUILD_PLAN.md). Delete this file when M5 starts. */
public class ServiceDemo {

    private static final UserService users = new UserService();
    private static final CompanyService companies = new CompanyService();
    private static final ServiceService services = new ServiceService();
    private static final PetService pets = new PetService();
    private static final ReservationService reservations = new ReservationService();
    private static final ReviewService reviews = new ReviewService();

    public static void main(String[] args) {
        Repositories.useMemory();
        reviews.refreshAllAverages();

        PetOwner anna = (PetOwner) user("anna", Role.PET_OWNER);
        PetOwner jonas = (PetOwner) user("jonas", Role.PET_OWNER);
        Caretaker tomas = (Caretaker) user("tomas", Role.CARETAKER);
        Caretaker lukas = (Caretaker) user("lukas", Role.CARETAKER);
        Caretaker egle = (Caretaker) user("egle", Role.CARETAKER);
        CompanyManager mantas = (CompanyManager) user("mantas", Role.COMPANY_MANAGER);
        Company letenele = companies.search("Letenėlė", null).get(0);
        Company uodegele = companies.search("Uodegėlė", null).get(0);
        Pet reksas = pets.search("Reksas", null, null).get(0);
        Service walking = services.search(ServiceType.DOG_WALKING, null, null, null).get(0);

        // ------------------------------------------------------------ validation
        title("VALIDATION: a pet with many bad fields (all errors at once)");
        Pet bad = new Pet();
        bad.setName("   ");
        bad.setWeightKg(-3);
        bad.setBirthDate(LocalDate.now().plusDays(1));
        try {
            pets.create(bad, null);
            System.out.println("  (no error - WRONG)");
        } catch (ValidationException e) {
            e.getErrors().forEach((field, message) -> System.out.println("  " + field + ": " + message));
        }

        // ------------------------------------------------------------ delete rules
        title("DELETE RULE: pet owner");
        attempt("delete Anna (has reservations)", () -> users.delete(anna));
        PetOwner petras = newOwner();
        Pet ciupis = pets.create(pet("Čiupis"), petras);
        System.out.println("  Petras has pets: " + petras.getPets() + ", pets in storage: " + pets.findAll().size());
        attempt("delete Petras (no reservations)", () -> users.delete(petras));
        System.out.println("  pets in storage after: " + pets.findAll().size()
                + ", Čiupis still stored: " + pets.findAll().contains(ciupis));

        title("DELETE RULE: pet");
        attempt("delete Reksas (has reservations)", () -> pets.delete(reksas));
        Pet kakis = pets.create(pet("Kakis"), jonas);
        attempt("delete Kakis (no reservations)", () -> pets.delete(kakis));
        System.out.println("  Jonas's pets after: " + jonas.getPets());

        title("DELETE RULE: caretaker");
        attempt("delete Tomas (has reservations)", () -> users.delete(tomas));
        Caretaker ona = newCaretaker();
        letenele.hireCaretaker(ona); // M8 adds a service method for this
        System.out.println("  Letenėlė caretakers: " + letenele.getCaretakers());
        attempt("delete Ona (no reservations)", () -> users.delete(ona));
        System.out.println("  Letenėlė caretakers after: " + letenele.getCaretakers());

        title("DELETE RULE: company");
        attempt("delete Letenėlė (has a manager)", () -> companies.delete(letenele));
        attempt("delete manager Mantas (no rule for managers)", () -> users.delete(mantas));
        attempt("delete Uodegėlė (no manager now, but booked services)", () -> companies.delete(uodegele));
        Company kailiukas = newCompany();
        Service bath = services.create(service("Maudynės"), kailiukas);
        kailiukas.hireCaretaker(lukas);
        System.out.println("  Kailiukas: services " + kailiukas.getServices() + ", caretakers " + kailiukas.getCaretakers());
        attempt("delete Kailiukas (nothing booked)", () -> companies.delete(kailiukas));
        System.out.println("  Maudynės still stored: " + services.findAll().contains(bath)
                + ", Lukas still a user: " + users.findAll().contains(lukas)
                + ", Lukas independent: " + lukas.isIndependent());

        title("DELETE RULE: service");
        attempt("delete Šunų vedžiojimas (has reservations)", () -> {
            boolean deleted = services.delete(walking);
            System.out.println("  really deleted: " + deleted + ", status now: " + walking.getStatus());
        });
        Service extra = services.create(service("Dantų valymas"), letenele);
        attempt("delete Dantų valymas (no reservations)", () -> {
            boolean deleted = services.delete(extra);
            System.out.println("  really deleted: " + deleted + ", Letenėlė services: " + letenele.getServices().size());
        });

        title("DELETE RULE: reservation (its reviews go too)");
        Reservation completed = reservations.search(ReservationStatus.COMPLETED, null, null, null).get(0);
        System.out.println("  before: reviews " + reviews.findAll().size()
                + ", Eglė avg " + egle.getAverageRating() + ", Anna avg " + anna.getAverageRating());
        attempt("delete the COMPLETED reservation", () -> reservations.delete(completed));
        System.out.println("  after:  reviews " + reviews.findAll().size()
                + ", Eglė avg " + egle.getAverageRating() + ", Anna avg " + anna.getAverageRating());
    }

    // ----------------------------------------------------------------- helpers

    private static void title(String text) {
        System.out.println();
        System.out.println("== " + text + " ==");
    }

    /** Runs an action and prints either OK or the rule's message. */
    private static void attempt(String label, Runnable action) {
        try {
            action.run();
            System.out.println("  OK       " + label);
        } catch (BusinessRuleException e) {
            System.out.println("  REFUSED  " + label + " -> " + e.getMessage());
        }
    }

    private static User user(String username, Role role) {
        return users.search(username, role).get(0);
    }

    private static PetOwner newOwner() {
        PetOwner o = new PetOwner();
        o.setUsername("petras");
        o.setFirstName("Petras");
        o.setLastName("Petraitis");
        o.setEmail("petras@gmail.com");
        users.create(o, "Slaptazodis1", "Slaptazodis1");
        return o;
    }

    private static Caretaker newCaretaker() {
        Caretaker c = new Caretaker();
        c.setUsername("ona");
        c.setFirstName("Ona");
        c.setLastName("Onaitė");
        c.setEmail("ona@gmail.com");
        users.create(c, "Slaptazodis1", "Slaptazodis1");
        return c;
    }

    private static Company newCompany() {
        Company c = new Company();
        c.setName("Kailiukas");
        c.setCompanyCode("111222333");
        c.setCity("Klaipėda");
        return companies.create(c);
    }

    private static Pet pet(String name) {
        Pet p = new Pet();
        p.setName(name);
        p.setSpecies(Species.CAT);
        p.setSize(PetSize.SMALL);
        p.setWeightKg(3.5);
        return p;
    }

    private static Service service(String title) {
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
}
