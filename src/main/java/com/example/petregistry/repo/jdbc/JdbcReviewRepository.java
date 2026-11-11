package com.example.petregistry.repo.jdbc;

import com.example.petregistry.model.Company;
import com.example.petregistry.model.Rateable;
import com.example.petregistry.model.Review;
import com.example.petregistry.model.User;
import com.example.petregistry.repo.ReviewRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Reviews in MySQL. Review.target is a Rateable: a user (owner / caretaker) OR a company.
 * One foreign key can't point to two tables, so there are two columns and exactly one is filled:
 * target_user_id or target_company_id.
 * Review.reservation is set only when the review is read through its reservation (reservation.addReview).
 */
public class JdbcReviewRepository implements ReviewRepository {

    private static final String SELECT = "SELECT * FROM reviews rv";

    @Override
    public List<Review> findAll() {
        return Sql.list(SELECT + " ORDER BY rv.id", JdbcReviewRepository::mapRow);
    }

    @Override
    public Optional<Review> findById(int id) {
        return Sql.one(SELECT + " WHERE rv.id = ?", JdbcReviewRepository::mapRow, id);
    }

    /** ReviewService links the reservation BEFORE calling create, so review.getReservation() is set here. */
    @Override
    public Review create(Review review) {
        int id = Sql.insert("""
                        INSERT INTO reviews (reservation_id, author_id, target_user_id, target_company_id, rating,
                                             pet_behavior_rating, comment, active, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                review.getReservation().getId(), review.getAuthor().getId(),
                targetUserId(review.getTarget()), targetCompanyId(review.getTarget()), review.getRating(),
                review.getPetBehaviorRating(), review.getComment(), review.isActive(), review.getCreatedAt());
        review.setId(id);
        return review;
    }

    /** The reservation and the author never change; the rest can. */
    @Override
    public void update(Review review) {
        Sql.execute("""
                        UPDATE reviews SET target_user_id = ?, target_company_id = ?, rating = ?,
                                           pet_behavior_rating = ?, comment = ?, active = ?
                        WHERE id = ?
                        """,
                targetUserId(review.getTarget()), targetCompanyId(review.getTarget()), review.getRating(),
                review.getPetBehaviorRating(), review.getComment(), review.isActive(), review.getId());
    }

    @Override
    public void delete(int id) {
        Sql.execute("DELETE FROM reviews WHERE id = ?", id);
    }

    @Override
    public List<Review> findByReservation(int reservationId) {
        return Sql.list(SELECT + " WHERE rv.reservation_id = ? ORDER BY rv.id", JdbcReviewRepository::mapRow,
                reservationId);
    }

    /** The column to look in depends on what kind of target it is. */
    @Override
    public List<Review> findByTarget(Rateable target) {
        if (target instanceof Company company) {
            return Sql.list(SELECT + " WHERE rv.target_company_id = ? ORDER BY rv.id", JdbcReviewRepository::mapRow,
                    company.getId());
        }
        return Sql.list(SELECT + " WHERE rv.target_user_id = ? ORDER BY rv.id", JdbcReviewRepository::mapRow,
                ((User) target).getId());
    }

    // ----------------------------------------------------------------- row → object

    /** Author and target are loaded plain: the review only needs their names and ids. */
    private static Review mapRow(ResultSet rs) throws SQLException {
        Review review = new Review();
        review.setId(rs.getInt("id"));
        review.setRating(rs.getInt("rating"));
        review.setPetBehaviorRating(Sql.nullableInt(rs, "pet_behavior_rating"));
        review.setComment(rs.getString("comment"));
        review.setActive(rs.getBoolean("active"));
        review.setCreatedAt(Sql.dateTime(rs, "created_at"));
        review.setAuthor(JdbcUserRepository.findPlain(rs.getInt("author_id")).orElse(null));

        Integer companyId = Sql.nullableInt(rs, "target_company_id");
        Integer userId = Sql.nullableInt(rs, "target_user_id");
        if (companyId != null) {
            review.setTarget(JdbcCompanyRepository.findPlain(companyId).orElse(null));
        } else if (userId != null) {
            // Only owners and caretakers are Rateable, and only they can be review targets.
            review.setTarget((Rateable) JdbcUserRepository.findPlain(userId).orElse(null));
        }
        return review;
    }

    // ----------------------------------------------------------------- object → columns

    private static Integer targetUserId(Rateable target) {
        return target instanceof User user ? user.getId() : null;
    }

    private static Integer targetCompanyId(Rateable target) {
        return target instanceof Company company ? company.getId() : null;
    }
}
