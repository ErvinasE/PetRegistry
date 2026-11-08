package com.example.petregistry.model;

import java.util.EnumSet;
import java.util.Set;

public enum ReservationStatus {
    PENDING("Laukiama"),
    CONFIRMED("Patvirtinta"),
    IN_PROGRESS("Vykdoma"),
    COMPLETED("Įvykdyta"),
    CANCELLED("Atšaukta"),
    REJECTED("Atmesta");

    private final String label;

    ReservationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** The statuses a reservation may move to from this one (the table in BUILD_PLAN.md). */
    public Set<ReservationStatus> nextStatuses() {
        return switch (this) {
            case PENDING -> EnumSet.of(CONFIRMED, REJECTED, CANCELLED);
            case CONFIRMED -> EnumSet.of(IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> EnumSet.of(COMPLETED);
            case COMPLETED, CANCELLED, REJECTED -> EnumSet.noneOf(ReservationStatus.class);
        };
    }

    public boolean canMoveTo(ReservationStatus target) {
        return nextStatuses().contains(target);
    }

    @Override
    public String toString() {
        return label;
    }
}
