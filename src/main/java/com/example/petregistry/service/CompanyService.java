package com.example.petregistry.service;

import com.example.petregistry.model.Caretaker;
import com.example.petregistry.model.Company;
import com.example.petregistry.model.Service;
import com.example.petregistry.repo.Repositories;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.example.petregistry.service.Checks.isBlank;
import static com.example.petregistry.service.Checks.throwIfAny;

public class CompanyService {

    private static final Pattern COMPANY_CODE = Pattern.compile("\\d{9}");

    public List<Company> findAll() {
        return Repositories.companies().findAll();
    }

    public List<Company> search(String name, String city) {
        return Repositories.companies().search(name, city);
    }

    public Company create(Company company) {
        validate(company);
        return Repositories.companies().create(company);
    }

    public void update(Company company) {
        validate(company);
        Repositories.companies().update(company);
    }

    /**
     * Rule: has managers or booked services -> refuse;
     * else delete its services, release its caretakers, delete the company.
     */
    public void delete(Company company) {
        int managers = Repositories.users().countManagersOf(company.getId());
        if (managers > 0) {
            throw new BusinessRuleException("Įmonės „" + company.getName()
                    + "“ pašalinti negalima: jai priskirta vadybininkų (" + managers + ")");
        }
        boolean booked = company.getServices().stream()
                .anyMatch(s -> Repositories.reservations().countByService(s.getId()) > 0);
        if (booked) {
            throw new BusinessRuleException("Įmonės „" + company.getName()
                    + "“ pašalinti negalima: jos paslaugos turi rezervacijų");
        }
        for (Service service : new ArrayList<>(company.getServices())) {
            company.removeService(service);
            Repositories.services().delete(service.getId());
        }
        for (Caretaker caretaker : new ArrayList<>(company.getCaretakers())) {
            company.releaseCaretaker(caretaker);
            Repositories.users().update(caretaker); // the caretaker stays, now without a company
        }
        Repositories.companies().delete(company.getId());
    }

    private void validate(Company company) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (isBlank(company.getName())) {
            errors.put("name", "Įveskite įmonės pavadinimą");
        }
        String code = company.getCompanyCode();
        if (isBlank(code)) {
            errors.put("companyCode", "Įveskite įmonės kodą");
        } else if (!COMPANY_CODE.matcher(code).matches()) {
            errors.put("companyCode", "Įmonės kodas turi būti 9 skaitmenys");
        } else if (Repositories.companies().findByCode(code)
                .filter(other -> other.getId() != company.getId()).isPresent()) {
            errors.put("companyCode", "Įmonė su tokiu kodu jau yra");
        }
        if (isBlank(company.getCity())) {
            errors.put("city", "Įveskite miestą");
        }
        throwIfAny(errors);
    }
}
