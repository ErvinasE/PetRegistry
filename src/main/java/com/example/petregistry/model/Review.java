package com.example.petregistry.model;

import java.time.LocalDateTime;

public class Review extends BaseEntity {
    private int rating;
    private String comment;
    private Integer petBehaviorRating;
    private LocalDateTime createdAt = LocalDateTime.now();
    private boolean active = true;
    private Reservation reservation;
    private User author;
    private Rateable target;

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Integer getPetBehaviorRating() {
        return petBehaviorRating;
    }

    public void setPetBehaviorRating(Integer petBehaviorRating) {
        this.petBehaviorRating = petBehaviorRating;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Reservation getReservation() {
        return reservation;
    }

    /** Package-private: call reservation.addReview / removeReview so both sides stay in sync. */
    void setReservation(Reservation reservation) {
        this.reservation = reservation;
    }

    public User getAuthor() {
        return author;
    }

    public void setAuthor(User author) {
        this.author = author;
    }

    public Rateable getTarget() {
        return target;
    }

    public void setTarget(Rateable target) {
        this.target = target;
    }

    @Override
    public String toString() {
        return author + ": " + rating + "/5";
    }
}
