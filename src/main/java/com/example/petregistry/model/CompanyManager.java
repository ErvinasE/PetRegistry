package com.example.petregistry.model;

public class CompanyManager extends User {
    private Company company;

    @Override
    public Role getRole() {
        return Role.COMPANY_MANAGER;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }
}
