package com.example.petregistry.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Holds the ONE shared database connection of the app (LAB2).
 * Opening a connection is slow (network + login), so we open it once on "Prisijungti…"
 * and reuse it for every query until "Atsijungti" or app exit.
 * That is why the JDBC repositories never close it (no try-with-resources on the connection).
 */
public final class DatabaseManager {

    private static Connection connection;

    private DatabaseManager() {
        // only static methods
    }

    /**
     * Opens the connection. A wrong password, a stopped server or a missing database
     * throws DataAccessException with a readable message, and the app stays in memory mode.
     */
    public static void connect(String url, String user, String password) {
        disconnect(); // never keep two connections
        try {
            connection = DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            throw new DataAccessException("Nepavyko prisijungti: " + e.getMessage(), e);
        }
    }

    /** Closes the connection if one is open. Safe to call many times (MainApp.stop() calls it too). */
    public static void disconnect() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            // Closing failed (e.g. the server is already gone). Nothing useful to do: we drop it anyway.
        } finally {
            connection = null;
        }
    }

    public static boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    /** The open connection, for the JDBC repositories. A clear error instead of a NullPointerException. */
    public static Connection getConnection() {
        if (!isConnected()) {
            throw new IllegalStateException("Neprisijungta prie duomenų bazės");
        }
        return connection;
    }

    /**
     * Runs several SQL changes as ONE transaction: either all of them are saved, or none.
     * autoCommit = true (the default) saves every statement at once. We turn it off, run the work,
     * then commit() (save all) or, on any error, rollback() (undo all). finally turns autoCommit back on.
     * If a transaction is already running (autoCommit is off), the work simply joins it,
     * so a service transaction can call a repository method that also asks for a transaction.
     */
    public static void inTransaction(Runnable work) {
        Connection c = getConnection();
        try {
            if (!c.getAutoCommit()) {
                work.run(); // already inside a transaction: the outer one commits or rolls back
                return;
            }
            c.setAutoCommit(false);
            try {
                work.run();
                c.commit();
            } catch (RuntimeException e) {
                c.rollback();
                throw e; // the caller still sees the original error
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw DataAccessException.of(e);
        }
    }
}
