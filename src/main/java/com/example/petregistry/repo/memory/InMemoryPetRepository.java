package com.example.petregistry.repo.memory;

import com.example.petregistry.model.Pet;
import com.example.petregistry.model.PetSize;
import com.example.petregistry.model.Species;
import com.example.petregistry.repo.PetRepository;

import java.time.LocalDate;
import java.util.List;

public class InMemoryPetRepository extends InMemoryRepository<Pet> implements PetRepository {

    @Override
    public List<Pet> search(String name, Species species, Integer ownerId, PetSize size,
                            LocalDate bornFrom, LocalDate bornTo, Double minWeight, Double maxWeight) {
        return stream()
                .filter(p -> matchesText(p.getName(), name))
                .filter(p -> species == null || p.getSpecies() == species)
                .filter(p -> matchesId(p.getOwner(), ownerId))
                .filter(p -> size == null || p.getSize() == size)
                // "not before" = on or after, so the from-day itself is included
                .filter(p -> bornFrom == null
                        || (p.getBirthDate() != null && !p.getBirthDate().isBefore(bornFrom)))
                .filter(p -> bornTo == null
                        || (p.getBirthDate() != null && !p.getBirthDate().isAfter(bornTo)))
                .filter(p -> minWeight == null || p.getWeightKg() >= minWeight)
                .filter(p -> maxWeight == null || p.getWeightKg() <= maxWeight)
                .toList();
    }
}
