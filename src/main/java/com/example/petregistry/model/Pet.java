package com.example.petregistry.model;

import java.time.LocalDate;
import java.time.Period;

public class Pet extends BaseEntity {
    private String name;
    private Species species;
    private PetSize size;
    private LocalDate birthDate;
    private double weightKg;
    private String notes;
    private String photoPath;
    private PetOwner owner;

    /** Age from the birth date until today, or null when the birth date is unknown. */
    public Period getAge() {
        return birthDate == null ? null : Period.between(birthDate, LocalDate.now());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Species getSpecies() {
        return species;
    }

    public void setSpecies(Species species) {
        this.species = species;
    }

    public PetSize getSize() {
        return size;
    }

    public void setSize(PetSize size) {
        this.size = size;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(double weightKg) {
        this.weightKg = weightKg;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public void setPhotoPath(String photoPath) {
        this.photoPath = photoPath;
    }

    public PetOwner getOwner() {
        return owner;
    }

    /** Package-private: call owner.addPet / removePet so both sides stay in sync. */
    void setOwner(PetOwner owner) {
        this.owner = owner;
    }

    @Override
    public String toString() {
        return name;
    }
}
