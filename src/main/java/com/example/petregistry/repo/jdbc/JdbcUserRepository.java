package com.example.petregistry.repo.jdbc;

import com.example.petregistry.model.Administrator;
import com.example.petregistry.model.Caretaker;
import com.example.petregistry.model.Company;
import com.example.petregistry.model.CompanyManager;
import com.example.petregistry.model.Pet;
import com.example.petregistry.model.PetOwner;
import com.example.petregistry.model.Rateable;
import com.example.petregistry.model.Role;
import com.example.petregistry.model.User;
import com.example.petregistry.repo.UserRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Users in MySQL. User + 4 subclasses live in ONE table "users"; the "role" column says which class to create.
 * Columns that only one subclass has (address, bio, company_id ...) are NULL for the others.
 */
public class JdbcUserRepository implements UserRepository {

    /** Every user query starts like this. avg_rating is calculated by SQL from ACTIVE reviews (NULL → 0), so it is never out of date. */
    private static final String SELECT = """
            SELECT u.*,
                   (SELECT AVG(r.rating) FROM reviews r WHERE r.target_user_id = u.id AND r.active) AS avg_rating
            FROM users u
            """;

    @Override
    public List<User> findAll() {
        return Sql.list(SELECT + " ORDER BY u.id", JdbcUserRepository::mapFull);
    }

    @Override
    public Optional<User> findById(int id) {
        return Sql.one(SELECT + " WHERE u.id = ?", JdbcUserRepository::mapFull, id);
    }

    @Override
    public User create(User user) {
        int id = Sql.insert("""
                        INSERT INTO users (role, username, password_hash, email, first_name, last_name, phone,
                                           blocked, created_at, address, bio, experience_years, company_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                user.getRole(), user.getUsername(), user.getPasswordHash(), user.getEmail(),
                user.getFirstName(), user.getLastName(), user.getPhone(), user.isBlocked(), user.getCreatedAt(),
                addressOf(user), bioOf(user), experienceOf(user), companyIdOf(user));
        user.setId(id);
        return user;
    }

    /** The role is not updated: an object can't change its class (the form locks the role box too). */
    @Override
    public void update(User user) {
        Sql.execute("""
                        UPDATE users SET username = ?, password_hash = ?, email = ?, first_name = ?, last_name = ?,
                                         phone = ?, blocked = ?, address = ?, bio = ?, experience_years = ?,
                                         company_id = ?
                        WHERE id = ?
                        """,
                user.getUsername(), user.getPasswordHash(), user.getEmail(), user.getFirstName(),
                user.getLastName(), user.getPhone(), user.isBlocked(), addressOf(user), bioOf(user),
                experienceOf(user), companyIdOf(user), user.getId());
    }

    @Override
    public void delete(int id) {
        Sql.execute("DELETE FROM users WHERE id = ?", id);
    }

    /** The WHERE is built from the filters that are set. Each "?" gets its value in the same order. */
    @Override
    public List<User> search(String text, Role role, Boolean blocked) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (!Sql.blank(text)) {
            // utf8mb4_unicode_ci collation: LIKE ignores upper/lower case, like the in-memory search.
            // The company name is in another table, so a subquery finds the ids of matching companies.
            sql.append(" AND (u.username LIKE ? OR CONCAT(u.first_name, ' ', u.last_name) LIKE ? OR u.email LIKE ?"
                    + " OR u.phone LIKE ? OR u.company_id IN (SELECT c.id FROM companies c WHERE c.name LIKE ?))");
            String pattern = Sql.contains(text);
            for (int i = 0; i < 5; i++) { // 5 "?" above, all get the same pattern
                params.add(pattern);
            }
        }
        if (role != null) {
            sql.append(" AND u.role = ?");
            params.add(role);
        }
        if (blocked != null) {
            sql.append(" AND u.blocked = ?");
            params.add(blocked);
        }
        sql.append(" ORDER BY u.id");
        return Sql.list(sql.toString(), JdbcUserRepository::mapFull, params.toArray());
    }

    @Override
    public int countManagersOf(int companyId) {
        return Sql.count("SELECT COUNT(*) FROM users WHERE role = 'COMPANY_MANAGER' AND company_id = ?", companyId);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return Sql.one(SELECT + " WHERE u.username = ?", JdbcUserRepository::mapFull, username);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return Sql.one(SELECT + " WHERE u.email = ?", JdbcUserRepository::mapFull, email);
    }

    @Override
    public List<PetOwner> findOwners() {
        return Sql.list(SELECT + " WHERE u.role = 'PET_OWNER' ORDER BY u.id",
                rs -> (PetOwner) mapFull(rs));
    }

    @Override
    public List<Caretaker> findCaretakers(Integer companyId) {
        if (companyId == null) {
            return Sql.list(SELECT + " WHERE u.role = 'CARETAKER' ORDER BY u.id", rs -> (Caretaker) mapFull(rs));
        }
        return Sql.list(SELECT + " WHERE u.role = 'CARETAKER' AND u.company_id = ? ORDER BY u.id",
                rs -> (Caretaker) mapFull(rs), companyId);
    }

    // ----------------------------------------------------------------- for the other JDBC repositories

    /** A user with only their own columns, no pets or company: loading the links too would go in circles. */
    static Optional<User> findPlain(int id) {
        return Sql.one(SELECT + " WHERE u.id = ?", JdbcUserRepository::mapRow, id);
    }

    /** A company's caretakers, plain (the company links them to itself with hireCaretaker). */
    static List<Caretaker> findPlainCaretakersOf(int companyId) {
        return Sql.list(SELECT + " WHERE u.role = 'CARETAKER' AND u.company_id = ? ORDER BY u.id",
                rs -> (Caretaker) mapRow(rs), companyId);
    }

    // ----------------------------------------------------------------- row → object

    /** One row → the right subclass, with its own columns only. The lists stay empty. */
    private static User mapRow(ResultSet rs) throws SQLException {
        Role role = Sql.enumOf(Role.class, rs, "role");
        User user = switch (role) {
            case ADMIN -> new Administrator();
            case COMPANY_MANAGER -> new CompanyManager();
            case CARETAKER -> new Caretaker();
            case PET_OWNER -> new PetOwner();
        };
        user.setId(rs.getInt("id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setEmail(rs.getString("email"));
        user.setFirstName(rs.getString("first_name"));
        user.setLastName(rs.getString("last_name"));
        user.setPhone(rs.getString("phone"));
        user.setBlocked(rs.getBoolean("blocked"));
        user.setCreatedAt(Sql.dateTime(rs, "created_at"));
        if (user instanceof PetOwner owner) {
            owner.setAddress(rs.getString("address"));
        }
        if (user instanceof Caretaker caretaker) {
            caretaker.setBio(rs.getString("bio"));
            caretaker.setExperienceYears(rs.getInt("experience_years")); // NULL → 0
        }
        if (user instanceof Rateable rateable) {
            rateable.setAverageRating(rs.getDouble("avg_rating"));
        }
        return user;
    }

    /** mapRow + the links, one extra query each. */
    private static User mapFull(ResultSet rs) throws SQLException {
        User user = mapRow(rs);
        Integer companyId = Sql.nullableInt(rs, "company_id");
        if (user instanceof PetOwner owner) {
            for (Pet pet : JdbcPetRepository.findPlainByOwner(owner.getId())) {
                owner.addPet(pet);
            }
        } else if (user instanceof Caretaker caretaker && companyId != null) {
            JdbcCompanyRepository.findPlain(companyId).ifPresent(company -> company.hireCaretaker(caretaker));
        } else if (user instanceof CompanyManager manager && companyId != null) {
            manager.setCompany(JdbcCompanyRepository.findPlain(companyId).orElse(null));
        }
        return user;
    }

    // ----------------------------------------------------------------- object → columns

    private static String addressOf(User user) {
        return user instanceof PetOwner owner ? owner.getAddress() : null;
    }

    private static String bioOf(User user) {
        return user instanceof Caretaker caretaker ? caretaker.getBio() : null;
    }

    private static Integer experienceOf(User user) {
        return user instanceof Caretaker caretaker ? caretaker.getExperienceYears() : null;
    }

    /** Managers and caretakers store their company's id; everyone else NULL. */
    private static Integer companyIdOf(User user) {
        Company company = null;
        if (user instanceof CompanyManager manager) {
            company = manager.getCompany();
        } else if (user instanceof Caretaker caretaker) {
            company = caretaker.getCompany();
        }
        return company == null ? null : company.getId();
    }
}
