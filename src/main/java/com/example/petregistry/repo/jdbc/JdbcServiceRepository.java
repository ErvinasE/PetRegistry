package com.example.petregistry.repo.jdbc;

import com.example.petregistry.db.DatabaseManager;
import com.example.petregistry.model.PetSize;
import com.example.petregistry.model.Service;
import com.example.petregistry.model.ServiceLocation;
import com.example.petregistry.model.ServiceStatus;
import com.example.petregistry.model.ServiceType;
import com.example.petregistry.model.Species;
import com.example.petregistry.repo.ServiceRepository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Services in MySQL. A Set can't be one column, so allowedSpecies / allowedSizes live in two small tables
 * (service_species, service_sizes) with one row per value. Saving = delete the old rows + insert the new ones.
 */
public class JdbcServiceRepository implements ServiceRepository {

    private static final String SELECT = "SELECT * FROM services s";

    @Override
    public List<Service> findAll() {
        return Sql.list(SELECT + " ORDER BY s.id", JdbcServiceRepository::mapFull);
    }

    @Override
    public Optional<Service> findById(int id) {
        return Sql.one(SELECT + " WHERE s.id = ?", JdbcServiceRepository::mapFull, id);
    }

    /** One service = 1 row in services + rows in service_species and service_sizes, in one transaction. */
    @Override
    public Service create(Service service) {
        DatabaseManager.inTransaction(() -> {
            int id = Sql.insert("""
                            INSERT INTO services (company_id, title, description, type, price, duration_minutes,
                                                  location, status)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    service.getCompany().getId(), service.getTitle(), service.getDescription(), service.getType(),
                    service.getPrice(), service.getDurationMinutes(), service.getLocation(), service.getStatus());
            service.setId(id);
            insertSets(service);
        });
        return service;
    }

    @Override
    public void update(Service service) {
        DatabaseManager.inTransaction(() -> {
            Sql.execute("""
                            UPDATE services SET company_id = ?, title = ?, description = ?, type = ?, price = ?,
                                                duration_minutes = ?, location = ?, status = ?
                            WHERE id = ?
                            """,
                    service.getCompany().getId(), service.getTitle(), service.getDescription(), service.getType(),
                    service.getPrice(), service.getDurationMinutes(), service.getLocation(), service.getStatus(),
                    service.getId());
            // Simplest correct way to save a set: forget the old rows, write the current ones.
            Sql.execute("DELETE FROM service_species WHERE service_id = ?", service.getId());
            Sql.execute("DELETE FROM service_sizes WHERE service_id = ?", service.getId());
            insertSets(service);
        });
    }

    /** The species / sizes rows go too: their foreign key has ON DELETE CASCADE. */
    @Override
    public void delete(int id) {
        Sql.execute("DELETE FROM services WHERE id = ?", id);
    }

    @Override
    public List<Service> search(ServiceType type, Integer companyId, BigDecimal minPrice, BigDecimal maxPrice) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (type != null) {
            sql.append(" AND s.type = ?");
            params.add(type);
        }
        if (companyId != null) {
            sql.append(" AND s.company_id = ?");
            params.add(companyId);
        }
        if (minPrice != null) {
            sql.append(" AND s.price >= ?");
            params.add(minPrice);
        }
        if (maxPrice != null) {
            sql.append(" AND s.price <= ?");
            params.add(maxPrice);
        }
        sql.append(" ORDER BY s.id");
        return Sql.list(sql.toString(), JdbcServiceRepository::mapFull, params.toArray());
    }

    // ----------------------------------------------------------------- for the other JDBC repositories

    /** A company's services without the company link (the company adds them to itself with addService). */
    static List<Service> findPlainByCompany(int companyId) {
        return Sql.list(SELECT + " WHERE s.company_id = ? ORDER BY s.id", JdbcServiceRepository::mapRow, companyId);
    }

    // ----------------------------------------------------------------- row → object

    /** The service's own columns AND its species / sizes (they are part of the service itself). */
    private static Service mapRow(ResultSet rs) throws SQLException {
        Service service = new Service();
        service.setId(rs.getInt("id"));
        service.setTitle(rs.getString("title"));
        service.setDescription(rs.getString("description"));
        service.setType(Sql.enumOf(ServiceType.class, rs, "type"));
        service.setPrice(rs.getBigDecimal("price"));
        service.setDurationMinutes(rs.getInt("duration_minutes"));
        service.setLocation(Sql.enumOf(ServiceLocation.class, rs, "location"));
        service.setStatus(Sql.enumOf(ServiceStatus.class, rs, "status"));
        service.setAllowedSpecies(speciesOf(service.getId()));
        service.setAllowedSizes(sizesOf(service.getId()));
        return service;
    }

    /** mapRow + the company (plain). addService also sets service.company. */
    private static Service mapFull(ResultSet rs) throws SQLException {
        Service service = mapRow(rs);
        JdbcCompanyRepository.findPlain(rs.getInt("company_id")).ifPresent(company -> company.addService(service));
        return service;
    }

    private static Set<Species> speciesOf(int serviceId) {
        Set<Species> species = EnumSet.noneOf(Species.class);
        species.addAll(Sql.list("SELECT species FROM service_species WHERE service_id = ?",
                rs -> Sql.enumOf(Species.class, rs, "species"), serviceId));
        return species;
    }

    private static Set<PetSize> sizesOf(int serviceId) {
        Set<PetSize> sizes = EnumSet.noneOf(PetSize.class);
        sizes.addAll(Sql.list("SELECT size FROM service_sizes WHERE service_id = ?",
                rs -> Sql.enumOf(PetSize.class, rs, "size"), serviceId));
        return sizes;
    }

    private static void insertSets(Service service) {
        for (Species species : service.getAllowedSpecies()) {
            Sql.execute("INSERT INTO service_species (service_id, species) VALUES (?, ?)", service.getId(), species);
        }
        for (PetSize size : service.getAllowedSizes()) {
            Sql.execute("INSERT INTO service_sizes (service_id, size) VALUES (?, ?)", service.getId(), size);
        }
    }
}
