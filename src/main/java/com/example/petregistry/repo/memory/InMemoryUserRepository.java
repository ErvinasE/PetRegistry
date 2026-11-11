package com.example.petregistry.repo.memory;

import com.example.petregistry.model.Caretaker;
import com.example.petregistry.model.Company;
import com.example.petregistry.model.CompanyManager;
import com.example.petregistry.model.PetOwner;
import com.example.petregistry.model.Role;
import com.example.petregistry.model.User;
import com.example.petregistry.repo.UserRepository;

import java.util.List;
import java.util.Optional;

public class InMemoryUserRepository extends InMemoryRepository<User> implements UserRepository {

    @Override
    public List<User> search(String text, Role role, Boolean blocked) {
        return stream()
                .filter(u -> matchesText(u.getUsername(), text)
                        || matchesText(u.getFullName(), text)
                        || matchesText(u.getEmail(), text)
                        || matchesText(u.getPhone(), text)
                        || matchesText(companyNameOf(u), text))
                .filter(u -> role == null || u.getRole() == role)
                .filter(u -> blocked == null || u.isBlocked() == blocked)
                .toList();
    }

    /** Managers and caretakers can have a company; everyone else: null. */
    private static String companyNameOf(User user) {
        Company company = null;
        if (user instanceof CompanyManager manager) {
            company = manager.getCompany();
        } else if (user instanceof Caretaker caretaker) {
            company = caretaker.getCompany();
        }
        return company == null ? null : company.getName();
    }

    @Override
    public int countManagersOf(int companyId) {
        return (int) stream()
                .filter(u -> u instanceof CompanyManager m && matchesId(m.getCompany(), companyId))
                .count();
    }

    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return stream()
                .filter(u -> username.equalsIgnoreCase(u.getUsername()))
                .findFirst();
    }

    @Override
    public Optional<User> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return stream()
                .filter(u -> email.equalsIgnoreCase(u.getEmail()))
                .findFirst();
    }

    @Override
    public List<PetOwner> findOwners() {
        return stream()
                .filter(u -> u instanceof PetOwner)
                .map(u -> (PetOwner) u)
                .toList();
    }

    @Override
    public List<Caretaker> findCaretakers(Integer companyId) {
        return stream()
                .filter(u -> u instanceof Caretaker)
                .map(u -> (Caretaker) u)
                .filter(c -> matchesId(c.getCompany(), companyId))
                .toList();
    }
}
