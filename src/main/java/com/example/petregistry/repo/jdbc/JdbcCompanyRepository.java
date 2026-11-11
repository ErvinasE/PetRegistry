package com.example.petregistry.repo.jdbc;

import com.example.petregistry.model.Caretaker;
import com.example.petregistry.model.Company;
import com.example.petregistry.model.Service;
import com.example.petregistry.repo.CompanyRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Companies in MySQL. Company.services and Company.caretakers are not columns: they are filled with extra queries. */
public class JdbcCompanyRepository implements CompanyRepository {

    /** avg_rating = average of the company's ACTIVE reviews, calculated by SQL on every read. */
    private static final String SELECT = """
            SELECT c.*,
                   (SELECT AVG(r.rating) FROM reviews r WHERE r.target_company_id = c.id AND r.active) AS avg_rating
            FROM companies c
            """;

    @Override
    public List<Company> findAll() {
        return Sql.list(SELECT + " ORDER BY c.id", JdbcCompanyRepository::mapFull);
    }

    @Override
    public Optional<Company> findById(int id) {
        return Sql.one(SELECT + " WHERE c.id = ?", JdbcCompanyRepository::mapFull, id);
    }

    @Override
    public Company create(Company company) {
        int id = Sql.insert("INSERT INTO companies (name, company_code, city, address, active) VALUES (?, ?, ?, ?, ?)",
                company.getName(), company.getCompanyCode(), company.getCity(), company.getAddress(), company.isActive());
        company.setId(id);
        return company;
    }

    /** Only the company's own columns. Its services and caretakers are saved by their own repositories. */
    @Override
    public void update(Company company) {
        Sql.execute("UPDATE companies SET name = ?, company_code = ?, city = ?, address = ?, active = ? WHERE id = ?",
                company.getName(), company.getCompanyCode(), company.getCity(), company.getAddress(),
                company.isActive(), company.getId());
    }

    @Override
    public void delete(int id) {
        Sql.execute("DELETE FROM companies WHERE id = ?", id);
    }

    @Override
    public List<Company> search(String text, String city, Boolean active) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (!Sql.blank(text)) {
            sql.append(" AND (c.name LIKE ? OR c.company_code LIKE ? OR c.address LIKE ?)");
            String pattern = Sql.contains(text);
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        if (!Sql.blank(city)) {
            sql.append(" AND c.city LIKE ?");
            params.add(Sql.contains(city));
        }
        if (active != null) {
            sql.append(" AND c.active = ?");
            params.add(active);
        }
        sql.append(" ORDER BY c.id");
        return Sql.list(sql.toString(), JdbcCompanyRepository::mapFull, params.toArray());
    }

    @Override
    public Optional<Company> findByCode(String companyCode) {
        if (companyCode == null) {
            return Optional.empty();
        }
        return Sql.one(SELECT + " WHERE c.company_code = ?", JdbcCompanyRepository::mapFull, companyCode);
    }

    // ----------------------------------------------------------------- for the other JDBC repositories

    /** A company with only its own columns (no services, no caretakers): used as a PART of a user or service. */
    static Optional<Company> findPlain(int id) {
        return Sql.one(SELECT + " WHERE c.id = ?", JdbcCompanyRepository::mapRow, id);
    }

    // ----------------------------------------------------------------- row → object

    private static Company mapRow(ResultSet rs) throws SQLException {
        Company company = new Company();
        company.setId(rs.getInt("id"));
        company.setName(rs.getString("name"));
        company.setCompanyCode(rs.getString("company_code"));
        company.setCity(rs.getString("city"));
        company.setAddress(rs.getString("address"));
        company.setActive(rs.getBoolean("active"));
        company.setAverageRating(rs.getDouble("avg_rating"));
        return company;
    }

    /** mapRow + its services and caretakers. addService / hireCaretaker also set the other side. */
    private static Company mapFull(ResultSet rs) throws SQLException {
        Company company = mapRow(rs);
        for (Service service : JdbcServiceRepository.findPlainByCompany(company.getId())) {
            company.addService(service);
        }
        for (Caretaker caretaker : JdbcUserRepository.findPlainCaretakersOf(company.getId())) {
            company.hireCaretaker(caretaker);
        }
        return company;
    }
}
