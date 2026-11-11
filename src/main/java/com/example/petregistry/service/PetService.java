package com.example.petregistry.service;

import com.example.petregistry.model.Pet;
import com.example.petregistry.model.PetOwner;
import com.example.petregistry.model.PetSize;
import com.example.petregistry.model.Species;
import com.example.petregistry.repo.Repositories;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.example.petregistry.service.Checks.isBlank;
import static com.example.petregistry.service.Checks.throwIfAny;

public class PetService {

    public List<Pet> findAll() {
        return Repositories.pets().findAll();
    }

    public List<Pet> search(String name, Species species, Integer ownerId, PetSize size,
                            LocalDate bornFrom, LocalDate bornTo, Double minWeight, Double maxWeight) {
        return Repositories.pets().search(name, species, ownerId, size, bornFrom, bornTo, minWeight, maxWeight);
    }

    /**
     * Validates, links the pet to its owner (both sides), then saves it.
     * Linking comes first: the INSERT needs the owner id.
     */
    public Pet create(Pet pet, PetOwner owner) {
        validate(pet, owner);
        owner.addPet(pet);
        Repositories.pets().create(pet);
        return pet;
    }

    /** If the owner changed, addPet moves the pet away from the old owner. */
    public void update(Pet pet, PetOwner owner) {
        validate(pet, owner);
        owner.addPet(pet);
        Repositories.pets().update(pet);
    }

    /** Rule: a pet with reservations can't be deleted. */
    public void delete(Pet pet) {
        int count = Repositories.reservations().countByPet(pet.getId());
        if (count > 0) {
            throw new BusinessRuleException("Gyvūno „" + pet.getName()
                    + "“ pašalinti negalima: jis turi rezervacijų (" + count + ")");
        }
        if (pet.getOwner() != null) {
            pet.getOwner().removePet(pet);
        }
        Repositories.pets().delete(pet.getId());
    }

    /** Checks the pet without saving. Public so the form can check a draft copy. */
    public void validate(Pet pet, PetOwner owner) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (isBlank(pet.getName())) {
            errors.put("name", "Įveskite gyvūno vardą");
        }
        if (pet.getSpecies() == null) {
            errors.put("species", "Pasirinkite rūšį");
        }
        if (pet.getSize() == null) {
            errors.put("size", "Pasirinkite dydį");
        }
        if (pet.getWeightKg() <= 0 || pet.getWeightKg() > 200) {
            errors.put("weightKg", "Svoris turi būti daugiau nei 0 ir ne daugiau kaip 200 kg");
        }
        if (pet.getBirthDate() != null && pet.getBirthDate().isAfter(LocalDate.now())) {
            errors.put("birthDate", "Gimimo data negali būti ateityje");
        }
        if (owner == null) {
            errors.put("owner", "Pasirinkite šeimininką");
        }
        throwIfAny(errors);
    }
}
