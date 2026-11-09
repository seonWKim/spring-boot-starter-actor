package io.github.seonwkim.actor.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/** Plain-SQL helpers shared by this module's stores. */
final class Jdbc {

    /** SQLSTATE class of integrity constraint violations, such as a duplicate key. */
    private static final String INTEGRITY_VIOLATION = "23";

    private Jdbc() {}

    /** The database's current time: H2, PostgreSQL and MySQL all run this statement. */
    static Timestamp now(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT CURRENT_TIMESTAMP");
                ResultSet row = statement.executeQuery()) {
            row.next();
            return row.getTimestamp(1);
        }
    }

    static boolean isDuplicateKey(SQLException e) {
        return e.getSQLState() != null && e.getSQLState().startsWith(INTEGRITY_VIOLATION);
    }
}
