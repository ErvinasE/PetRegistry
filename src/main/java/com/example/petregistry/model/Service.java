package com.example.petregistry.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public class Service extends BaseEntity {
    private String title;
    private String description;
    private ServiceType type;
    private BigDecimal price;
    private int durationMinutes;
    private ServiceLocation location;
    private ServiceStatus status = ServiceStatus.ACTIVE;
    private final Set<Species> allowedSpecies = EnumSet.noneOf(Species.class);
    private final Set<PetSize> allowedSizes = EnumSet.noneOf(PetSize.class);
    private Company company;

    /** Basic guard only. The 0-10 000 range rule lives in ServiceService.validate(). */
    public void changePrice(BigDecimal newPrice) {
        if (newPrice == null || newPrice.signum() < 0) {
            throw new IllegalArgumentException("Kaina negali būti tuščia ar neigiama");
        }
        this.price = newPrice;
    }

    public void setTemporarilyUnavailable(boolean flag) {
        if (status == ServiceStatus.INACTIVE) {
            throw new IllegalStateException("Neaktyvios paslaugos būsenos keisti negalima");
        }
        status = flag ? ServiceStatus.TEMPORARILY_UNAVAILABLE : ServiceStatus.ACTIVE;
    }

    public void deactivate() {
        status = ServiceStatus.INACTIVE;
    }

    public boolean accepts(Pet pet) {
        return pet != null
                && allowedSpecies.contains(pet.getSpecies())
                && allowedSizes.contains(pet.getSize());
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ServiceType getType() {
        return type;
    }

    public void setType(ServiceType type) {
        this.type = type;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public ServiceLocation getLocation() {
        return location;
    }

    public void setLocation(ServiceLocation location) {
        this.location = location;
    }

    public ServiceStatus getStatus() {
        return status;
    }

    public void setStatus(ServiceStatus status) {
        this.status = status;
    }

    public Set<Species> getAllowedSpecies() {
        return Collections.unmodifiableSet(allowedSpecies);
    }

    public void setAllowedSpecies(Set<Species> species) {
        allowedSpecies.clear();
        allowedSpecies.addAll(species);
    }

    public Set<PetSize> getAllowedSizes() {
        return Collections.unmodifiableSet(allowedSizes);
    }

    public void setAllowedSizes(Set<PetSize> sizes) {
        allowedSizes.clear();
        allowedSizes.addAll(sizes);
    }

    public Company getCompany() {
        return company;
    }

    /** Package-private: call company.addService / removeService so both sides stay in sync. */
    void setCompany(Company company) {
        this.company = company;
    }

    @Override
    public String toString() {
        return title;
    }
}
