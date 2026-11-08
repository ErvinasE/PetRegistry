package com.example.petregistry.model;

public enum Species {
    DOG("Šuo"),
    CAT("Katė"),
    BIRD("Paukštis"),
    RODENT("Graužikas"),
    OTHER("Kita");

    private final String label;

    Species(String label) {
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
