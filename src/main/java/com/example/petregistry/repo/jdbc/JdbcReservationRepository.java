package com.example.petregistry.repo.jdbc;

import com.example.petregistry.model.Caretaker;
import com.example.petregistry.model.PetOwner;
import com.example.petregistry.model.Reservation;
import com.example.petregistry.model.ReservationStatus;
import com.example.petregistry.model.Review;
import com.example.petregistry.repo.ReservationRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reservations in MySQL. "start" and "end" are SQL keywords, so the columns are start_at / end_at.
 * A reservation points to 4 other objects; each is loaded with its own repository (one query each).
 */
public class JdbcReservationRepository implements ReservationRepository {

    private static final String SELECT = "SELECT * FROM reservations r";

    // Full loaders for the parts. These repositories have no fields, so creating them here is cheap and safe.
    private final JdbcUserRepository users = new JdbcUserRepository();
    private final JdbcPetRepository pets = new JdbcPetRepository();
    private final JdbcServiceRepository services = new JdbcServiceRepository();
    private final JdbcReviewRepository reviews = new JdbcReviewRepository();

    @Override
    public List<Reservation> findAll() {
        return Sql.list(SELECT + " ORDER BY r.id", this::mapRow);
    }

    @Override
    public Optional<Reservation> findById(int id) {
        return Sql.one(SELECT + " WHERE r.id = ?", this::mapRow, id);
    }

    @Override
    public Reservation create(Reservation reservation) {
        int id = Sql.insert("""
                        INSERT INTO reservations (owner_id, pet_id, service_id, caretaker_id, start_at, end_at,
                                                  status, price, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                reservation.getOwner().getId(), reservation.getPet().getId(), reservation.getService().getId(),
                caretakerId(reservation), reservation.getStart(), reservation.getEnd(), reservation.getStatus(),
                reservation.getPrice(), reservation.getCreatedAt());
        reservation.setId(id);
        return reservation;
    }

    /** Also used by "Keisti būseną": the status column is saved with everything else. */
    @Override
    public void update(Reservation reservation) {
        Sql.execute("""
                        UPDATE reservations SET owner_id = ?, pet_id = ?, service_id = ?, caretaker_id = ?,
                                                start_at = ?, end_at = ?, status = ?, price = ?
                        WHERE id = ?
                        """,
                reservation.getOwner().getId(), reservation.getPet().getId(), reservation.getService().getId(),
                caretakerId(reservation), reservation.getStart(), reservation.getEnd(), reservation.getStatus(),
                reservation.getPrice(), reservation.getId());
    }

    /** ReservationService deletes the reviews first (the foreign key would block this otherwise). */
    @Override
    public void delete(int id) {
        Sql.execute("DELETE FROM reservations WHERE id = ?", id);
    }

    /** The date filter includes both days: from = day 00:00, to = before the next day 00:00. */
    @Override
    public List<Reservation> search(ReservationStatus status, Integer ownerId, LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (status != null) {
            sql.append(" AND r.status = ?");
            params.add(status);
        }
        if (ownerId != null) {
            sql.append(" AND r.owner_id = ?");
            params.add(ownerId);
        }
        if (from != null) {
            sql.append(" AND r.start_at >= ?");
            params.add(from.atStartOfDay());
        }
        if (to != null) {
            sql.append(" AND r.start_at < ?");
            params.add(to.plusDays(1).atStartOfDay());
        }
        sql.append(" ORDER BY r.id");
        return Sql.list(sql.toString(), this::mapRow, params.toArray());
    }


    @Override
    public int countByPet(int petId) {
        return Sql.count("SELECT COUNT(*) FROM reservations WHERE pet_id = ?", petId);
    }

    @Override
    public int countByOwner(int ownerId) {
        return Sql.count("SELECT COUNT(*) FROM reservations WHERE owner_id = ?", ownerId);
    }

    @Override
    public int countByCaretaker(int caretakerId) {
        return Sql.count("SELECT COUNT(*) FROM reservations WHERE caretaker_id = ?", caretakerId);
    }

    @Override
    public int countByService(int serviceId) {
        return Sql.count("SELECT COUNT(*) FROM reservations WHERE service_id = ?", serviceId);
    }

    // ----------------------------------------------------------------- row → object

    private Reservation mapRow(ResultSet rs) throws SQLException {
        Reservation reservation = new Reservation();
        reservation.setId(rs.getInt("id"));
        reservation.setStart(Sql.dateTime(rs, "start_at"));
        reservation.setEnd(Sql.dateTime(rs, "end_at"));
        reservation.setStatus(Sql.enumOf(ReservationStatus.class, rs, "status"));
        reservation.setPrice(rs.getBigDecimal("price"));
        reservation.setCreatedAt(Sql.dateTime(rs, "created_at"));

        reservation.setOwner((PetOwner) users.findById(rs.getInt("owner_id")).orElse(null));
        reservation.setPet(pets.findById(rs.getInt("pet_id")).orElse(null));
        reservation.setService(services.findById(rs.getInt("service_id")).orElse(null));
        Integer caretakerId = Sql.nullableInt(rs, "caretaker_id");
        if (caretakerId != null) {
            reservation.setCaretaker((Caretaker) users.findById(caretakerId).orElse(null));
        }
        // The reviews list is not a column: one more query. addReview also sets review.reservation.
        for (Review review : reviews.findByReservation(reservation.getId())) {
            reservation.addReview(review);
        }
        return reservation;
    }

    private static Integer caretakerId(Reservation reservation) {
        return reservation.getCaretaker() == null ? null : reservation.getCaretaker().getId();
    }
}
