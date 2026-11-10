package com.example.petregistry.service;

import com.example.petregistry.model.Caretaker;
import com.example.petregistry.model.CompanyManager;
import com.example.petregistry.model.Pet;
import com.example.petregistry.model.PetOwner;
import com.example.petregistry.model.Role;
import com.example.petregistry.model.User;
import com.example.petregistry.repo.Repositories;
import org.mindrot.jbcrypt.BCrypt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.example.petregistry.service.Checks.isBlank;
import static com.example.petregistry.service.Checks.throwIfAny;

public class UserService {

    // Compiled once (static final), not on every validate() call.
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,30}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Pattern PHONE = Pattern.compile("\\+370\\d{8}");
    private static final int MIN_PASSWORD_LENGTH = 8;

    public List<User> findAll() {
        return Repositories.users().findAll();
    }

    public List<User> search(String text, Role role) {
        return Repositories.users().search(text, role);
    }

    /** For owner choice lists (Pets tab, reservation form). */
    public List<PetOwner> findOwners() {
        return Repositories.users().findOwners();
    }

    /** For caretaker choice lists. companyId = null means all caretakers. */
    public List<Caretaker> findCaretakers(Integer companyId) {
        return Repositories.users().findCaretakers(companyId);
    }

    /** On create, the password is required and both fields must be equal. Only the hash is stored. */
    public User create(User user, String password, String passwordRepeat) {
        validate(user, password, passwordRepeat);
        user.setPasswordHash(hash(password));
        return Repositories.users().create(user);
    }

    /** On edit, blank password fields mean "keep the old password". */
    public void update(User user, String password, String passwordRepeat) {
        validate(user, password, passwordRepeat);
        if (wantsNewPassword(password, passwordRepeat)) {
            user.setPasswordHash(hash(password));
        }
        Repositories.users().update(user);
    }

    /**
     * Checks the user and the password fields without saving anything. create/update call it too.
     * A new user (id 0) must get a password; an existing one only if something was typed.
     * Public so the form can check a draft copy BEFORE it changes the stored user.
     */
    public void validate(User user, String password, String passwordRepeat) {
        Map<String, String> errors = validateFields(user);
        if (user.isNew() || wantsNewPassword(password, passwordRepeat)) {
            checkPassword(password, passwordRepeat, errors);
        }
        throwIfAny(errors);
    }

    /** Blocking isn't a form edit: no validation, no password fields. A blocked user can't log in. */
    public void setBlocked(User user, boolean blocked) {
        user.setBlocked(blocked);
        Repositories.users().update(user);
    }

    public void delete(User user) {
        if (user instanceof PetOwner owner) {
            deleteOwner(owner);
        } else if (user instanceof Caretaker caretaker) {
            deleteCaretaker(caretaker);
        } else {
            // managers and administrators have no delete rule
            Repositories.users().delete(user.getId());
        }
    }

    /** Rule: has reservations -> refuse; else delete their pets, then the owner. */
    private void deleteOwner(PetOwner owner) {
        int count = Repositories.reservations().countByOwner(owner.getId());
        if (count > 0) {
            throw new BusinessRuleException("Šeimininko „" + owner.getFullName()
                    + "“ pašalinti negalima: yra jo rezervacijų (" + count + ")");
        }
        // Loop over a COPY: removePet changes the real list, and changing a list
        // while looping over it throws ConcurrentModificationException.
        for (Pet pet : new ArrayList<>(owner.getPets())) {
            owner.removePet(pet);
            Repositories.pets().delete(pet.getId());
        }
        Repositories.users().delete(owner.getId());
    }

    /** Rule: has reservations -> refuse; else release from the company, then delete. */
    private void deleteCaretaker(Caretaker caretaker) {
        int count = Repositories.reservations().countByCaretaker(caretaker.getId());
        if (count > 0) {
            throw new BusinessRuleException("Prižiūrėtojo „" + caretaker.getFullName()
                    + "“ pašalinti negalima: jam priskirta rezervacijų (" + count + ")");
        }
        if (caretaker.getCompany() != null) {
            caretaker.getCompany().releaseCaretaker(caretaker);
        }
        Repositories.users().delete(caretaker.getId());
    }

    /** Checks the user fields. Returns the errors instead of throwing, so password errors can be added. */
    private Map<String, String> validateFields(User user) {
        Map<String, String> errors = new LinkedHashMap<>();

        String username = user.getUsername();
        if (isBlank(username)) {
            errors.put("username", "Įveskite prisijungimo vardą");
        } else if (!USERNAME.matcher(username).matches()) {
            errors.put("username", "Prisijungimo vardas: 3–30 simbolių, tik raidės, skaitmenys ir _");
        } else if (takenByOther(Repositories.users().findByUsername(username), user)) {
            errors.put("username", "Toks prisijungimo vardas jau užimtas");
        }

        String email = user.getEmail();
        if (isBlank(email)) {
            errors.put("email", "Įveskite el. paštą");
        } else if (!EMAIL.matcher(email).matches()) {
            errors.put("email", "Neteisingas el. pašto formatas");
        } else if (takenByOther(Repositories.users().findByEmail(email), user)) {
            errors.put("email", "Toks el. paštas jau naudojamas");
        }

        if (isBlank(user.getFirstName())) {
            errors.put("firstName", "Įveskite vardą");
        }
        if (isBlank(user.getLastName())) {
            errors.put("lastName", "Įveskite pavardę");
        }
        if (!isBlank(user.getPhone()) && !PHONE.matcher(user.getPhone()).matches()) {
            errors.put("phone", "Telefonas turi būti +370 ir 8 skaitmenys");
        }
        if (user instanceof CompanyManager manager && manager.getCompany() == null) {
            errors.put("company", "Vadybininkui būtina nurodyti įmonę");
        }
        if (user instanceof Caretaker caretaker && caretaker.getExperienceYears() < 0) {
            errors.put("experienceYears", "Patirtis negali būti neigiama");
        }
        return errors;
    }

    /** On edit, empty password fields mean "keep the old one". Typing in either field means "change it". */
    private static boolean wantsNewPassword(String password, String passwordRepeat) {
        return !isBlank(password) || !isBlank(passwordRepeat);
    }

    private void checkPassword(String password, String passwordRepeat, Map<String, String> errors) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            errors.put("password", "Slaptažodis turi būti bent " + MIN_PASSWORD_LENGTH + " simbolių");
        }
        if (!Objects.equals(password, passwordRepeat)) {
            errors.put("passwordRepeat", "Slaptažodžiai nesutampa");
        }
    }

    /** "Unique" check: found someone, and it is not the same user being edited. */
    private static boolean takenByOther(Optional<User> found, User user) {
        return found.isPresent() && found.get().getId() != user.getId();
    }

    private static String hash(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }
}
