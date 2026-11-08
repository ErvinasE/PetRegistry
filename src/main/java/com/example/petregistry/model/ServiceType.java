package com.example.petregistry.model;

public enum ServiceType {
    VETERINARY("Veterinarija"),
    GROOMING("Kirpimas ir priežiūra"),
    PET_SITTING("Augintinio priežiūra"),
    DOG_WALKING("Šunų vedžiojimas"),
    BOARDING("Augintinių viešbutis");

    private final String label;

    ServiceType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
