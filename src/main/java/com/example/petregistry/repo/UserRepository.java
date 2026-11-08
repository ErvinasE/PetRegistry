package com.example.petregistry.repo;

import com.example.petregistry.model.Caretaker;
import com.example.petregistry.model.PetOwner;
import com.example.petregistry.model.Role;
import com.example.petregistry.model.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends Repository<User> {

    /**
     * Filtered list for the Users tab. A null (or blank text) argument means "don't filter by this".
     *
     * @param text part of the username, full name or email, case-insensitive
     * @param role exact role
     */
    List<User> search(String text, Role role);

    /** For the company delete rule: how many managers work for this company. */
    int countManagersOf(int companyId);

    /** For the "unique username" check. Case-insensitive, like MySQL's default comparison. */
    Optional<User> findByUsername(String username);

    /** For the "unique email" check. Case-insensitive. */
    Optional<User> findByEmail(String email);

    /** For owner choice lists. */
    List<PetOwner> findOwners();

    /** For caretaker choice lists. companyId = null means all caretakers, also independent ones. */
    List<Caretaker> findCaretakers(Integer companyId);
}
