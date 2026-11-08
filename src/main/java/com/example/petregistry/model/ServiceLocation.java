package com.example.petregistry.model;

public enum ServiceLocation {
    PROVIDER_PREMISES("Teikėjo patalpose"),
    CLIENT_HOME("Kliento namuose"),
    OUTDOORS("Lauke");

    private final String label;

    ServiceLocation(String label) {
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
