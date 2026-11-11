package com.example.petregistry.repo.memory;

import com.example.petregistry.model.BaseEntity;
import com.example.petregistry.model.Reservation;
import com.example.petregistry.model.ReservationStatus;
import com.example.petregistry.repo.ReservationRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

public class InMemoryReservationRepository extends InMemoryRepository<Reservation>
        implements ReservationRepository {

    @Override
    public List<Reservation> search(ReservationStatus status, Integer ownerId, LocalDate from, LocalDate to,
                                    String text, BigDecimal minPrice, BigDecimal maxPrice) {
        return stream()
                .filter(r -> status == null || r.getStatus() == status)
                .filter(r -> matchesId(r.getOwner(), ownerId))
                // "not before" = on or after, so the from-day itself is included
                .filter(r -> from == null
                        || (r.getStart() != null && !r.getStart().toLocalDate().isBefore(from)))
                .filter(r -> to == null
                        || (r.getStart() != null && !r.getStart().toLocalDate().isAfter(to)))
                .filter(r -> matchesText(r.getPet() == null ? null : r.getPet().getName(), text)
                        || matchesText(r.getService() == null ? null : r.getService().getTitle(), text)
                        || matchesText(r.getCaretaker() == null ? null : r.getCaretaker().getFullName(), text))
                .filter(r -> minPrice == null
                        || (r.getPrice() != null && r.getPrice().compareTo(minPrice) >= 0))
                .filter(r -> maxPrice == null
                        || (r.getPrice() != null && r.getPrice().compareTo(maxPrice) <= 0))
                .toList();
    }

    @Override
    public int countByPet(int petId) {
        return countWhere(Reservation::getPet, petId);
    }

    @Override
    public int countByOwner(int ownerId) {
        return countWhere(Reservation::getOwner, ownerId);
    }

    @Override
    public int countByCaretaker(int caretakerId) {
        return countWhere(Reservation::getCaretaker, caretakerId);
    }

    @Override
    public int countByService(int serviceId) {
        return countWhere(Reservation::getService, serviceId);
    }

    /** Counts reservations whose part (pet, owner, ...) has this id. `part` says which getter to use. */
    private int countWhere(Function<Reservation, ? extends BaseEntity> part, int id) {
        return (int) stream()
                .filter(r -> matchesId(part.apply(r), id))
                .count();
    }
}
