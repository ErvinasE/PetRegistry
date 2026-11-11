package com.example.petregistry.service;

import com.example.petregistry.model.Caretaker;
import com.example.petregistry.model.Pet;
import com.example.petregistry.model.PetOwner;
import com.example.petregistry.model.Rateable;
import com.example.petregistry.model.Reservation;
import com.example.petregistry.model.ReservationStatus;
import com.example.petregistry.model.Review;
import com.example.petregistry.model.Service;
import com.example.petregistry.model.ServiceStatus;
import com.example.petregistry.repo.Repositories;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.example.petregistry.service.Checks.throwIfAny;

public class ReservationService {

    private final ReviewService reviewService = new ReviewService();

    public List<Reservation> findAll() {
        return Repositories.reservations().findAll();
    }

    public List<Reservation> search(ReservationStatus status, Integer ownerId, LocalDate from, LocalDate to,
                                    String text, BigDecimal minPrice, BigDecimal maxPrice) {
        return Repositories.reservations().search(status, ownerId, from, to, text, minPrice, maxPrice);
    }

    /** end = start + service duration. The price is COPIED from the service, so later price changes don't rewrite old bookings. */
    public Reservation create(Reservation reservation) {
        validate(reservation);
        reservation.setEnd(endOf(reservation));
        reservation.setPrice(reservation.getService().getPrice());
        return Repositories.reservations().create(reservation);
    }

    /** The end time follows the start. The price stays as it was when booked. */
    public void update(Reservation reservation) {
        validate(reservation);
        reservation.setEnd(endOf(reservation));
        Repositories.reservations().update(reservation);
    }

    /** Rule: delete its reviews, then the reservation, then refresh the averages those reviews affected. */
    public void delete(Reservation reservation) {
        List<Review> reviews = Repositories.reviews().findByReservation(reservation.getId());
        Set<Rateable> targets = new HashSet<>();
        // One transaction: the reviews and the reservation go together, or nothing is deleted.
        Repositories.inTransaction(() -> {
            for (Review review : reviews) {
                targets.add(review.getTarget());
                reservation.removeReview(review);
                Repositories.reviews().delete(review.getId());
            }
            Repositories.reservations().delete(reservation.getId());
        });
        targets.forEach(reviewService::refreshAverage);
    }

    /** The "Keisti būseną" button. The model checks the transition table; we turn its IllegalStateException into a rule message. */
    public void changeStatus(Reservation reservation, ReservationStatus target) {
        try {
            switch (target) {
                case CONFIRMED -> reservation.confirm();
                case REJECTED -> reservation.reject();
                case CANCELLED -> reservation.cancel();
                case IN_PROGRESS -> reservation.start();
                case COMPLETED -> reservation.complete();
                case PENDING -> throw new IllegalStateException("Grąžinti į „" + target + "“ negalima");
            }
        } catch (IllegalStateException e) {
            throw new BusinessRuleException(e.getMessage());
        }
        Repositories.reservations().update(reservation);
    }

    private static LocalDateTime endOf(Reservation reservation) {
        return reservation.getStart().plusMinutes(reservation.getService().getDurationMinutes());
    }

    /** Checks the reservation without saving. Public so the form can check a draft copy. */
    public void validate(Reservation reservation) {
        Map<String, String> errors = new LinkedHashMap<>();
        PetOwner owner = reservation.getOwner();
        Pet pet = reservation.getPet();
        Service service = reservation.getService();
        Caretaker caretaker = reservation.getCaretaker();
        LocalDateTime start = reservation.getStart();
        // "service ACTIVE" and "start in the future" only apply before the visit starts.
        boolean notStartedYet = reservation.getStatus() == ReservationStatus.PENDING
                || reservation.getStatus() == ReservationStatus.CONFIRMED;

        if (owner == null) {
            errors.put("owner", "Pasirinkite šeimininką");
        }

        if (pet == null) {
            errors.put("pet", "Pasirinkite gyvūną");
        } else if (owner != null && !owner.equals(pet.getOwner())) {
            errors.put("pet", "Šis gyvūnas nepriklauso pasirinktam šeimininkui");
        }

        if (service == null) {
            errors.put("service", "Pasirinkite paslaugą");
        } else {
            if (notStartedYet && service.getStatus() != ServiceStatus.ACTIVE) {
                errors.put("service", "Ši paslauga šiuo metu neteikiama");
            }
            if (pet != null && !service.accepts(pet)) {
                // putIfAbsent: keep the first "pet" message if there is one already
                errors.putIfAbsent("pet", "Paslauga netinka šiam gyvūnui (rūšis ar dydis)");
            }
        }

        if (caretaker != null && service != null
                && (caretaker.getCompany() == null || !caretaker.getCompany().equals(service.getCompany()))) {
            errors.put("caretaker", "Prižiūrėtojas nedirba šios paslaugos įmonėje");
        }

        if (start == null) {
            errors.put("start", "Pasirinkite pradžios laiką");
        } else if (notStartedYet && !start.isAfter(LocalDateTime.now())) {
            errors.put("start", "Pradžia turi būti ateityje");
        }
        throwIfAny(errors);
    }
}
