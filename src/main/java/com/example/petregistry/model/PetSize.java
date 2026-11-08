package com.example.petregistry.model;

public enum PetSize {
    SMALL("Mažas"),
    MEDIUM("Vidutinis"),
    LARGE("Didelis");

    private final String label;

    PetSize(String label) {
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
