package com.example.petregistry.service;

import com.example.petregistry.model.Rateable;
import com.example.petregistry.model.Reservation;
import com.example.petregistry.model.ReservationStatus;
import com.example.petregistry.model.Review;
import com.example.petregistry.model.User;
import com.example.petregistry.repo.Repositories;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.example.petregistry.service.Checks.throwIfAny;

public class ReviewService {

    public List<Review> findAll() {
        return Repositories.reviews().findAll();
    }

    public List<Review> findByReservation(int reservationId) {
        return Repositories.reviews().findByReservation(reservationId);
    }

    /** Validates, saves, links the review to its reservation, and updates the target's average. */
    public Review create(Review review, Reservation reservation) {
        validate(review, reservation);
        Repositories.reviews().create(review);
        reservation.addReview(review);
        refreshAverage(review.getTarget());
        return review;
    }

    public void update(Review review) {
        validate(review, review.getReservation());
        Repositories.reviews().update(review);
        refreshAverage(review.getTarget());
    }

    public void delete(Review review) {
        Rateable target = review.getTarget();
        if (review.getReservation() != null) {
            review.getReservation().removeReview(review);
        }
        Repositories.reviews().delete(review.getId());
        refreshAverage(target);
    }

    /** Hides a review without deleting it. Inactive reviews don't count in the average. */
    public void deactivate(Review review) {
        review.setActive(false);
        Repositories.reviews().update(review);
        refreshAverage(review.getTarget());
    }

    /**
     * LAB1: the only place that sets averageRating. Average of the target's ACTIVE reviews, 0 if none.
     * Call after every review create / remove / deactivate, and after deleting a reservation with reviews.
     */
    public void refreshAverage(Rateable target) {
        if (target == null) {
            return;
        }
        double average = Repositories.reviews().findByTarget(target).stream()
                .filter(Review::isActive)
                .mapToInt(Review::getRating)
                .average()
                .orElse(0);
        target.setAverageRating(average);
    }

    /** Recalculates every average. Call once after loading data (e.g. after Repositories.useMemory()). */
    public void refreshAllAverages() {
        for (User user : Repositories.users().findAll()) {
            if (user instanceof Rateable rateable) {
                refreshAverage(rateable);
            }
        }
        Repositories.companies().findAll().forEach(this::refreshAverage);
    }

    private void validate(Review review, Reservation reservation) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (reservation == null) {
            errors.put("reservation", "Nenurodyta rezervacija");
        } else if (reservation.getStatus() != ReservationStatus.COMPLETED) {
            errors.put("reservation", "Atsiliepimą galima palikti tik įvykdytai rezervacijai");
        }

        User author = review.getAuthor();
        if (author == null) {
            errors.put("author", "Pasirinkite autorių");
        } else if (reservation != null && !reservation.isParticipant(author)) {
            errors.put("author", "Autorius turi būti rezervacijos šeimininkas arba prižiūrėtojas");
        } else if (reservation != null && alreadyReviewed(review, reservation)) {
            errors.put("author", "Šis autorius jau paliko atsiliepimą šiai rezervacijai");
        }

        if (review.getTarget() == null) {
            errors.put("target", "Pasirinkite, ką vertinate");
        }
        if (review.getRating() < 1 || review.getRating() > 5) {
            errors.put("rating", "Įvertinimas turi būti nuo 1 iki 5");
        }
        Integer behavior = review.getPetBehaviorRating();
        if (behavior != null && (behavior < 1 || behavior > 5)) {
            errors.put("petBehaviorRating", "Gyvūno elgesio įvertinimas turi būti nuo 1 iki 5");
        }
        throwIfAny(errors);
    }

    /** One review per (reservation, author). The review being edited doesn't count against itself. */
    private boolean alreadyReviewed(Review review, Reservation reservation) {
        return Repositories.reviews().findByReservation(reservation.getId()).stream()
                .anyMatch(other -> other.getId() != review.getId()
                        && review.getAuthor().equals(other.getAuthor()));
    }
}
