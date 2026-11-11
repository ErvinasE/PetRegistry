package com.example.petregistry.repo;

import com.example.petregistry.model.Company;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends Repository<Company> {

    /**
     * Filtered list for the Companies tab. A null (or blank) argument means "don't filter by this".
     *
     * @param text   part of the name, company code or address, case-insensitive
     * @param city   part of the city, case-insensitive
     * @param active true = only active companies, false = only not active
     */
    List<Company> search(String text, String city, Boolean active);

    /** For the "unique company code" check. */
    Optional<Company> findByCode(String companyCode);
}
