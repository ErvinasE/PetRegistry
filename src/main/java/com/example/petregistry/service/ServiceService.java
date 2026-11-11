package com.example.petregistry.service;

import com.example.petregistry.model.Company;
import com.example.petregistry.model.Service;
import com.example.petregistry.model.ServiceLocation;
import com.example.petregistry.model.ServiceStatus;
import com.example.petregistry.model.ServiceType;
import com.example.petregistry.repo.Repositories;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.example.petregistry.service.Checks.isBlank;
import static com.example.petregistry.service.Checks.throwIfAny;

/** Service for the model class Service. */
public class ServiceService {

    private static final BigDecimal MAX_PRICE = new BigDecimal("10000");

    public List<Service> findAll() {
        return Repositories.services().findAll();
    }

    public List<Service> search(ServiceType type, Integer companyId, BigDecimal minPrice, BigDecimal maxPrice,
                                String title, Integer minDuration, Integer maxDuration,
                                ServiceLocation location, ServiceStatus status) {
        return Repositories.services().search(type, companyId, minPrice, maxPrice,
                title, minDuration, maxDuration, location, status);
    }

    /** Only ACTIVE services can be booked (the reservation form's list). */
    public List<Service> findActive() {
        return Repositories.services().findAll().stream()
                .filter(s -> s.getStatus() == ServiceStatus.ACTIVE)
                .toList();
    }

    /**
     * Validates, links the service to its company (both sides), then saves it.
     * Linking comes first: the INSERT needs the company id.
     */
    public Service create(Service service, Company company) {
        validate(service, company);
        company.addService(service);
        Repositories.services().create(service);
        return service;
    }

    /** If the company changed, addService moves the service away from the old company. */
    public void update(Service service, Company company) {
        validate(service, company);
        company.addService(service);
        Repositories.services().update(service);
    }

    /**
     * Rule: has reservations -> deactivate instead (kept for history); else remove from company and delete.
     *
     * @return true if really deleted, false if it was only deactivated (the UI shows an info alert)
     */
    public boolean delete(Service service) {
        if (Repositories.reservations().countByService(service.getId()) > 0) {
            service.deactivate();
            Repositories.services().update(service);
            return false;
        }
        if (service.getCompany() != null) {
            service.getCompany().removeService(service);
        }
        Repositories.services().delete(service.getId());
        return true;
    }

    /** The "Laikinai neteikiama" button: ACTIVE <-> TEMPORARILY_UNAVAILABLE. An INACTIVE service can't be paused. */
    public void setTemporarilyUnavailable(Service service, boolean flag) {
        try {
            service.setTemporarilyUnavailable(flag);
        } catch (IllegalStateException e) {
            throw new BusinessRuleException(e.getMessage());
        }
        Repositories.services().update(service);
    }

    /** Checks the service without saving. Public so the form can check a draft copy. */
    public void validate(Service service, Company company) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (isBlank(service.getTitle())) {
            errors.put("title", "Įveskite pavadinimą");
        }
        if (service.getType() == null) {
            errors.put("type", "Pasirinkite paslaugos tipą");
        }
        BigDecimal price = service.getPrice();
        if (price == null) {
            errors.put("price", "Įveskite kainą");
        } else if (price.signum() < 0 || price.compareTo(MAX_PRICE) > 0) {
            errors.put("price", "Kaina turi būti nuo 0 iki 10 000");
        }
        int minutes = service.getDurationMinutes();
        if (minutes < 15 || minutes > 1440 || minutes % 15 != 0) {
            errors.put("durationMinutes", "Trukmė turi būti 15–1440 min. ir 15 min. kartotinis");
        }
        if (service.getLocation() == null) {
            errors.put("location", "Pasirinkite vietą");
        }
        if (service.getAllowedSpecies().isEmpty()) {
            errors.put("allowedSpecies", "Pasirinkite bent vieną gyvūno rūšį");
        }
        if (service.getAllowedSizes().isEmpty()) {
            errors.put("allowedSizes", "Pasirinkite bent vieną dydį");
        }
        if (company == null) {
            errors.put("company", "Pasirinkite įmonę");
        }
        throwIfAny(errors);
    }
}
