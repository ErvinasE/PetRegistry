package com.example.petregistry.repo.memory;

import com.example.petregistry.model.Service;
import com.example.petregistry.model.ServiceLocation;
import com.example.petregistry.model.ServiceStatus;
import com.example.petregistry.model.ServiceType;
import com.example.petregistry.repo.ServiceRepository;

import java.math.BigDecimal;
import java.util.List;

public class InMemoryServiceRepository extends InMemoryRepository<Service> implements ServiceRepository {

    @Override
    public List<Service> search(ServiceType type, Integer companyId, BigDecimal minPrice, BigDecimal maxPrice,
                                String title, Integer minDuration, Integer maxDuration,
                                ServiceLocation location, ServiceStatus status) {
        return stream()
                .filter(s -> type == null || s.getType() == type)
                .filter(s -> matchesId(s.getCompany(), companyId))
                // compareTo, not equals: BigDecimal equals() says 10.0 != 10.00
                .filter(s -> minPrice == null
                        || (s.getPrice() != null && s.getPrice().compareTo(minPrice) >= 0))
                .filter(s -> maxPrice == null
                        || (s.getPrice() != null && s.getPrice().compareTo(maxPrice) <= 0))
                .filter(s -> matchesText(s.getTitle(), title))
                .filter(s -> minDuration == null || s.getDurationMinutes() >= minDuration)
                .filter(s -> maxDuration == null || s.getDurationMinutes() <= maxDuration)
                .filter(s -> location == null || s.getLocation() == location)
                .filter(s -> status == null || s.getStatus() == status)
                .toList();
    }
}
