package com.example.petregistry.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PetOwner extends User implements Rateable {
    private String address;
    private final List<Pet> pets = new ArrayList<>();
    private double averageRating;

    @Override
    public Role getRole() {
        return Role.PET_OWNER;
    }

    public void addPet(Pet pet) {
        if (pet == null || pets.contains(pet)) {
            return;
        }
        // a pet has one owner: take it away from the old one first
        PetOwner oldOwner = pet.getOwner();
        if (oldOwner != null && oldOwner != this) {
            oldOwner.removePet(pet);
        }
        pets.add(pet);
        pet.setOwner(this);
    }

    public void removePet(Pet pet) {
        if (pets.remove(pet)) {
            pet.setOwner(null);
        }
    }

    public List<Pet> getPets() {
        return Collections.unmodifiableList(pets);
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    @Override
    public double getAverageRating() {
        return averageRating;
    }

    @Override
    public void setAverageRating(double averageRating) {
        this.averageRating = averageRating;
    }
}
