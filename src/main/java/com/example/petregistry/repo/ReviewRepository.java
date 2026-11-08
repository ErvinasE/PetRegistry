package com.example.petregistry.repo;

import com.example.petregistry.model.Rateable;
import com.example.petregistry.model.Review;

import java.util.List;

public interface ReviewRepository extends Repository<Review> {

    /** All reviews of one reservation. */
    List<Review> findByReservation(int reservationId);

    /** All reviews about one caretaker, owner or company. The target must not be null. */
    List<Review> findByTarget(Rateable target);
}
