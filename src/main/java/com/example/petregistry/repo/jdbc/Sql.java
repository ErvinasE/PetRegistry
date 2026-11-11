package com.example.petregistry.repo.jdbc;

import com.example.petregistry.db.DataAccessException;
import com.example.petregistry.db.DatabaseManager;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The JDBC code every repository needs, written once.
 * Every query is a PreparedStatement: the SQL has "?" and the values are sent separately,
 * so a value like  ' OR 1=1 --  is just text, never SQL (no SQL injection).
 * try-with-resources closes the statement and the result set, NEVER the shared connection.
 * Package-private: only the repo.jdbc package uses it.
 */
final class Sql {

    private Sql() {
    }

    /** Turns one row of a ResultSet into an object. Like a Go "scan" function. */
    @FunctionalInterface
    interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    /**
     * SELECT many rows.
     * Note: the mapper may run more queries (e.g. load a pet's owner) while we read the rows.
     * That is safe with MySQL Connector/J: by default it reads the whole result into memory first.
     */
    static <T> List<T> list(String sql, RowMapper<T> mapper, Object... params) {
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapper.map(rs));
                }
                return result;
            }
        } catch (SQLException e) {
            throw DataAccessException.of(e);
        }
    }

    /** SELECT zero or one row. */
    static <T> Optional<T> one(String sql, RowMapper<T> mapper, Object... params) {
        List<T> rows = list(sql, mapper, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** SELECT COUNT(*) ... */
    static int count(String sql, Object... params) {
        return one(sql, rs -> rs.getInt(1), params).orElse(0);
    }

    /** UPDATE / DELETE / INSERT without a new id. Returns how many rows changed. */
    static int execute(String sql, Object... params) {
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw DataAccessException.of(e);
        }
    }

    /** INSERT and return the id that AUTO_INCREMENT gave the new row (RETURN_GENERATED_KEYS). */
    static int insert(String sql, Object... params) {
        try (PreparedStatement ps = DatabaseManager.getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, params);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("The database returned no id for the new row");
                }
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw DataAccessException.of(e);
        }
    }

    /**
     * Puts each Java value into its "?" with the right setXxx method.
     * JDBC counts from 1, not 0. Enums are stored by name(): Species.DOG → 'DOG'.
     */
    static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            int index = i + 1;
            Object value = params[i];
            if (value == null) {
                ps.setNull(index, Types.NULL);
            } else if (value instanceof String s) {
                ps.setString(index, s);
            } else if (value instanceof Integer n) {
                ps.setInt(index, n);
            } else if (value instanceof Double d) {
                ps.setDouble(index, d);
            } else if (value instanceof Boolean b) {
                ps.setBoolean(index, b);
            } else if (value instanceof BigDecimal money) {
                ps.setBigDecimal(index, money);
            } else if (value instanceof Enum<?> e) {
                ps.setString(index, e.name());
            } else if (value instanceof LocalDate date) {
                ps.setDate(index, Date.valueOf(date));
            } else if (value instanceof LocalDateTime dateTime) {
                ps.setTimestamp(index, Timestamp.valueOf(dateTime));
            } else {
                throw new IllegalArgumentException("Unsupported parameter type: " + value.getClass());
            }
        }
    }

    // ----------------------------------------------------------------- reading helpers

    /** A nullable INT column: getInt() returns 0 for NULL, so wasNull() tells them apart. */
    static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    static LocalDate date(ResultSet rs, String column) throws SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    static LocalDateTime dateTime(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    static <E extends Enum<E>> E enumOf(Class<E> type, ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : Enum.valueOf(type, value);
    }

    /** "%text%" for LIKE. % and _ typed by the user are escaped, so "a_b" means the text a_b, not a wildcard. */
    static String contains(String text) {
        String escaped = text.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /** True when a text filter is empty, i.e. "don't filter by this" (same rule as the in-memory search). */
    static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
