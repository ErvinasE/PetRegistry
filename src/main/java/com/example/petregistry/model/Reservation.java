package com.example.petregistry.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Reservation extends BaseEntity {
    private LocalDateTime start;
    private LocalDateTime end;
    private ReservationStatus status = ReservationStatus.PENDING;
    private BigDecimal price;
    private LocalDateTime createdAt = LocalDateTime.now();
    private PetOwner owner;
    private Pet pet;
    private Service service;
    private Caretaker caretaker;
    private final List<Review> reviews = new ArrayList<>();

    public void confirm() {
        moveTo(ReservationStatus.CONFIRMED);
    }

    public void reject() {
        moveTo(ReservationStatus.REJECTED);
    }

    public void cancel() {
        moveTo(ReservationStatus.CANCELLED);
    }

    public void start() {
        moveTo(ReservationStatus.IN_PROGRESS);
    }

    public void complete() {
        moveTo(ReservationStatus.COMPLETED);
    }

    private void moveTo(ReservationStatus target) {
        if (!status.canMoveTo(target)) {
            throw new IllegalStateException(
                    "Negalima keisti būsenos iš „" + status + "“ į „" + target + "“");
        }
        status = target;
    }

    public boolean isParticipant(User u) {
        return u != null && (u.equals(owner) || u.equals(caretaker));
    }

    public void addReview(Review r) {
        if (r == null || reviews.contains(r)) {
            return;
        }
        reviews.add(r);
        r.setReservation(this);
    }

    public void removeReview(Review r) {
        if (reviews.remove(r)) {
            r.setReservation(null);
        }
    }

    public List<Review> getReviews() {
        return Collections.unmodifiableList(reviews);
    }

    public LocalDateTime getStart() {
        return start;
    }

    public void setStart(LocalDateTime start) {
        this.start = start;
    }

    public LocalDateTime getEnd() {
        return end;
    }

    public void setEnd(LocalDateTime end) {
        this.end = end;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    /** For loading from storage only. Use confirm() / cancel() / ... to change status in the app. */
    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public PetOwner getOwner() {
        return owner;
    }

    public void setOwner(PetOwner owner) {
        this.owner = owner;
    }

    public Pet getPet() {
        return pet;
    }

    public void setPet(Pet pet) {
        this.pet = pet;
    }

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public Caretaker getCaretaker() {
        return caretaker;
    }

    public void setCaretaker(Caretaker caretaker) {
        this.caretaker = caretaker;
    }

    @Override
    public String toString() {
        return service + " – " + pet + ", " + start;
    }
}
