package com.example.petregistry.repo;

import com.example.petregistry.model.Pet;
import com.example.petregistry.model.Species;

import java.util.List;

public interface PetRepository extends Repository<Pet> {

    /**
     * Filtered list for the Pets tab. A null (or blank name) argument means "don't filter by this".
     *
     * @param name    part of the pet name, case-insensitive
     * @param species exact species
     * @param ownerId id of the owner
     */
    List<Pet> search(String name, Species species, Integer ownerId);
}
