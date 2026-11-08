package com.example.petregistry.model;

public class Caretaker extends User implements Rateable {
    private String bio;
    private int experienceYears;
    private Company company;
    private double averageRating;

    @Override
    public Role getRole() {
        return Role.CARETAKER;
    }

    public boolean isIndependent() {
        return company == null;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public int getExperienceYears() {
        return experienceYears;
    }

    public void setExperienceYears(int experienceYears) {
        this.experienceYears = experienceYears;
    }

    public Company getCompany() {
        return company;
    }

    /** Package-private: call company.hireCaretaker / releaseCaretaker so both sides stay in sync. */
    void setCompany(Company company) {
        this.company = company;
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
