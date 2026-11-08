package com.example.petregistry.model;

public class Administrator extends User {
    @Override
    public Role getRole() {
        return Role.ADMIN;
    }
}
