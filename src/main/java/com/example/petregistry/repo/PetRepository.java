package com.example.petregistry.repo;

import com.example.petregistry.model.Pet;
import com.example.petregistry.model.PetSize;
import com.example.petregistry.model.Species;

import java.time.LocalDate;
import java.util.List;

public interface PetRepository extends Repository<Pet> {

    /**
     * Filtered list for the Pets tab. A null (or blank name) argument means "don't filter by this".
     *
     * @param name    part of the pet name, case-insensitive
     * @param species exact species
     * @param ownerId   id of the owner
     * @param size      exact size
     * @param bornFrom  birth date on or after this day
     * @param bornTo    birth date on or before this day
     * @param minWeight weight in kg at least this (inclusive)
     * @param maxWeight weight in kg at most this (inclusive)
     */
    List<Pet> search(String name, Species species, Integer ownerId, PetSize size,
                     LocalDate bornFrom, LocalDate bornTo, Double minWeight, Double maxWeight);
}
