package com.example.petregistry.model;

public enum ServiceStatus {
    ACTIVE("Aktyvi"),
    TEMPORARILY_UNAVAILABLE("Laikinai neteikiama"),
    INACTIVE("Neaktyvi");

    private final String label;

    ServiceStatus(String label) {
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
