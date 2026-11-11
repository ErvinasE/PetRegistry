package com.example.petregistry.repo.memory;

import com.example.petregistry.model.Company;
import com.example.petregistry.repo.CompanyRepository;

import java.util.List;
import java.util.Optional;

public class InMemoryCompanyRepository extends InMemoryRepository<Company> implements CompanyRepository {

    @Override
    public List<Company> search(String text, String city, Boolean active) {
        return stream()
                .filter(c -> matchesText(c.getName(), text)
                        || matchesText(c.getCompanyCode(), text)
                        || matchesText(c.getAddress(), text))
                .filter(c -> matchesText(c.getCity(), city))
                .filter(c -> active == null || c.isActive() == active)
                .toList();
    }

    @Override
    public Optional<Company> findByCode(String companyCode) {
        if (companyCode == null) {
            return Optional.empty();
        }
        return stream()
                .filter(c -> companyCode.equals(c.getCompanyCode()))
                .findFirst();
    }
}
