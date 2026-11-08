package com.example.petregistry.repo.memory;

import com.example.petregistry.model.Rateable;
import com.example.petregistry.model.Review;
import com.example.petregistry.repo.ReviewRepository;

import java.util.List;

public class InMemoryReviewRepository extends InMemoryRepository<Review> implements ReviewRepository {

    @Override
    public List<Review> findByReservation(int reservationId) {
        return stream()
                .filter(r -> r.getReservation() != null && r.getReservation().getId() == reservationId)
                .toList();
    }

    @Override
    public List<Review> findByTarget(Rateable target) {
        // equals() comes from BaseEntity: same class and same id
        return stream()
                .filter(r -> target.equals(r.getTarget()))
                .toList();
    }
}
