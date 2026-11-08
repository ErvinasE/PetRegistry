package com.example.petregistry.repo;

import com.example.petregistry.model.Service;
import com.example.petregistry.model.ServiceType;

import java.math.BigDecimal;
import java.util.List;

public interface ServiceRepository extends Repository<Service> {

    /**
     * Filtered list for the Services tab. A null argument means "don't filter by this".
     *
     * @param type      exact service type
     * @param companyId id of the company that offers it
     * @param minPrice  price at least this (inclusive)
     * @param maxPrice  price at most this (inclusive)
     */
    List<Service> search(ServiceType type, Integer companyId, BigDecimal minPrice, BigDecimal maxPrice);
}
