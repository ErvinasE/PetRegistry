package com.example.petregistry.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Company extends BaseEntity implements Rateable {
    private String name;
    private String companyCode;
    private String city;
    private String address;
    private boolean active = true;
    private final List<Service> services = new ArrayList<>();
    private final List<Caretaker> caretakers = new ArrayList<>();
    private double averageRating;

    public void addService(Service s) {
        if (s == null || services.contains(s)) {
            return;
        }
        Company oldCompany = s.getCompany();
        if (oldCompany != null && oldCompany != this) {
            oldCompany.removeService(s);
        }
        services.add(s);
        s.setCompany(this);
    }

    public void removeService(Service s) {
        if (services.remove(s)) {
            s.setCompany(null);
        }
    }

    public void hireCaretaker(Caretaker c) {
        if (c == null || caretakers.contains(c)) {
            return;
        }
        Company oldCompany = c.getCompany();
        if (oldCompany != null && oldCompany != this) {
            oldCompany.releaseCaretaker(c);
        }
        caretakers.add(c);
        c.setCompany(this);
    }

    public void releaseCaretaker(Caretaker c) {
        if (caretakers.remove(c)) {
            c.setCompany(null);
        }
    }

    public List<Service> getServices() {
        return Collections.unmodifiableList(services);
    }

    public List<Caretaker> getCaretakers() {
        return Collections.unmodifiableList(caretakers);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCompanyCode() {
        return companyCode;
    }

    public void setCompanyCode(String companyCode) {
        this.companyCode = companyCode;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public double getAverageRating() {
        return averageRating;
    }

    @Override
    public void setAverageRating(double averageRating) {
        this.averageRating = averageRating;
    }

    @Override
    public String toString() {
        return name;
    }
}
