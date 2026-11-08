package com.example.petregistry.model;

public enum Role {
    ADMIN("Administratorius"),
    COMPANY_MANAGER("Įmonės vadovas"),
    CARETAKER("Prižiūrėtojas"),
    PET_OWNER("Augintinio šeimininkas");

    private final String label;

    Role(String label) {
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
