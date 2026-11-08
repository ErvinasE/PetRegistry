package com.example.petregistry.model;

import org.mindrot.jbcrypt.BCrypt;

import java.time.LocalDateTime;
import java.util.Objects;

public abstract class User extends BaseEntity {
    private String username;
    private String passwordHash;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private boolean blocked;
    private LocalDateTime createdAt = LocalDateTime.now();

    public abstract Role getRole();

    public String getFullName() {
        return (Objects.toString(firstName, "") + " " + Objects.toString(lastName, "")).trim();
    }

    public boolean checkPassword(String rawPassword) {
        return rawPassword != null && passwordHash != null && BCrypt.checkpw(rawPassword, passwordHash);
    }

    public boolean canLogin() {
        return !blocked;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return getFullName();
    }
}
