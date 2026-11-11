package com.example.petregistry.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** How dates and times are shown on screen. One place, so every table and form looks the same. */
public final class Formats {

    /** 2026-11-14 10:00 */
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Formats() {
    }

    /** The date and time as text, or "" when there is none. */
    public static String dateTime(LocalDateTime value) {
        return value == null ? "" : DATE_TIME.format(value);
    }
}
