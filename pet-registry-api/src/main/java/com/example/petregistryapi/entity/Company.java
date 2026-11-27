package com.example.petregistryapi.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;
    private String companyCode;
    private String city;
    private String address;
    private boolean active;

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCompanyCode() {
        return companyCode;
    }

    public String getCity() {
        return city;
    }

    public String getAddress() {
        return address;
    }

    public boolean isActive() {
        return active;
    }
}
