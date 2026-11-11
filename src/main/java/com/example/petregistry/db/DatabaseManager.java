package com.example.petregistry.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Holds the one shared database connection of the app.
 * It is opened on "Prisijungti…" and reused until "Atsijungti" or exit, so the repositories never close it.
 */
public final class DatabaseManager {

    private static Connection connection;

    private DatabaseManager() {
    }

    /** Opens the connection. On failure throws DataAccessException and the app stays in memory mode. */
    public static void connect(String url, String user, String password) {
        disconnect();
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
     * Runs several SQL changes as ONE transaction: commit all, or rollback all on an error.
     * If a transaction is already running, the work joins it.
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
