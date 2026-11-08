package com.example.petregistry.repo;

import com.example.petregistry.model.Company;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends Repository<Company> {

    /**
     * Filtered list for the Companies tab. A null (or blank) argument means "don't filter by this".
     *
     * @param name part of the company name, case-insensitive
     * @param city part of the city, case-insensitive
     */
    List<Company> search(String name, String city);

    /** For the "unique company code" check. */
    Optional<Company> findByCode(String companyCode);
}
