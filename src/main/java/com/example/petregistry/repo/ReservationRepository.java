package com.example.petregistry.repo;

import com.example.petregistry.model.Reservation;
import com.example.petregistry.model.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ReservationRepository extends Repository<Reservation> {

    /**
     * Filtered list for the Reservations tab. A null argument means "don't filter by this".
     *
     * @param status  exact status
     * @param ownerId id of the pet owner who booked it
     * @param from    start date on or after this day
     * @param to      start date on or before this day
     * @param text     part of the pet name, service title or caretaker name, case-insensitive
     * @param minPrice price at least this (inclusive)
     * @param maxPrice price at most this (inclusive)
     */
    List<Reservation> search(ReservationStatus status, Integer ownerId, LocalDate from, LocalDate to,
                             String text, BigDecimal minPrice, BigDecimal maxPrice);

    // Lookups for the delete rules ("does this pet have reservations?").

    int countByPet(int petId);

    int countByOwner(int ownerId);

    int countByCaretaker(int caretakerId);

    int countByService(int serviceId);
}
