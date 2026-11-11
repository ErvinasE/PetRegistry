package com.example.petregistry.db;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

/**
 * A database error with a message the user can read.
 * Unchecked (a RuntimeException), so services and controllers don't need "throws SQLException" everywhere:
 * java.sql stays inside the db and repo.jdbc packages.
 */
public class DataAccessException extends RuntimeException {

    // MySQL / MariaDB error codes (SQLException.getErrorCode())
    private static final int DUPLICATE_VALUE = 1062;        // UNIQUE column already has this value
    private static final int ROW_IS_REFERENCED = 1451;      // DELETE blocked: another table points to this row
    private static final int REFERENCED_ROW_MISSING = 1452; // INSERT/UPDATE points to a row that doesn't exist

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Turns a raw SQLException into a readable message. The original stays as the "cause" for debugging. */
    public static DataAccessException of(SQLException e) {
        if (e instanceof SQLIntegrityConstraintViolationException) {
            String message = switch (e.getErrorCode()) {
                case DUPLICATE_VALUE -> "Toks įrašas jau yra (pasikartojanti unikali reikšmė).";
                case ROW_IS_REFERENCED -> "Įrašo pašalinti negalima: jį naudoja kiti įrašai.";
                case REFERENCED_ROW_MISSING -> "Susietas įrašas nerastas duomenų bazėje.";
                default -> "Duomenys pažeidžia duomenų bazės taisyklę: " + e.getMessage();
            };
            return new DataAccessException(message, e);
        }
        // SQLState "08..." = connection problems (server stopped, network lost).
        if (e.getSQLState() != null && e.getSQLState().startsWith("08")) {
            return new DataAccessException("Ryšys su duomenų baze nutrūko. Prisijunkite iš naujo.", e);
        }
        return new DataAccessException("Duomenų bazės klaida: " + e.getMessage(), e);
    }
}
