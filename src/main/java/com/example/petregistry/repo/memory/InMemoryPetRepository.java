package com.example.petregistry.repo.memory;

import com.example.petregistry.model.Pet;
import com.example.petregistry.model.Species;
import com.example.petregistry.repo.PetRepository;

import java.util.List;

public class InMemoryPetRepository extends InMemoryRepository<Pet> implements PetRepository {

    @Override
    public List<Pet> search(String name, Species species, Integer ownerId) {
        return stream()
                .filter(p -> matchesText(p.getName(), name))
                .filter(p -> species == null || p.getSpecies() == species)
                .filter(p -> matchesId(p.getOwner(), ownerId))
                .toList();
    }
}
