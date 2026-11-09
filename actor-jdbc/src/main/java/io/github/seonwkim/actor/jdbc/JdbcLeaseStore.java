package io.github.seonwkim.actor.jdbc;

import io.github.seonwkim.actor.spi.LeaseStore;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * A {@link LeaseStore} in the {@code actor_lease} table of the application's {@link DataSource}.
 * Each change is one conditional {@code UPDATE} (or the first {@code INSERT}), so the database
 * decides which of several concurrent acquirers wins. Expiry is measured on the database's clock:
 * each call reads {@code CURRENT_TIMESTAMP} and compares and extends {@code expires_at} with it,
 * so the nodes' clocks need not agree. The table is created from {@link JdbcPeerRegistry#SCHEMA_LOCATION}.
 */
public final class JdbcLeaseStore implements LeaseStore {

    private static final String TAKE = "UPDATE actor_lease SET owner = ?, expires_at = ?"
            + " WHERE lease_name = ? AND (owner = ? OR expires_at < ?)";
    private static final String INSERT = "INSERT INTO actor_lease (owner, expires_at, lease_name) VALUES (?, ?, ?)";
    private static final String RENEW =
            "UPDATE actor_lease SET expires_at = ? WHERE lease_name = ? AND owner = ? AND expires_at >= ?";
    private static final String RELEASE = "DELETE FROM actor_lease WHERE lease_name = ? AND owner = ?";
    private static final String HOLDER = "SELECT owner FROM actor_lease WHERE lease_name = ? AND expires_at >= ?";

    private final DataSource dataSource;

    public JdbcLeaseStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean tryAcquire(String lease, String owner, Duration ttl) {
        try (Connection connection = dataSource.getConnection()) {
            Timestamp now = Jdbc.now(connection);
            Timestamp expires = plus(now, ttl);
            if (take(connection, lease, owner, now, expires)) {
                return true;
            }
            try (PreparedStatement insert = connection.prepareStatement(INSERT)) {
                insert.setString(1, owner);
                insert.setTimestamp(2, expires);
                insert.setString(3, lease);
                return insert.executeUpdate() == 1;
            } catch (SQLException e) {
                if (!Jdbc.isDuplicateKey(e)) {
                    throw e;
                }
                // Another owner inserted it first: it holds the lease, unless that already expired.
                return take(connection, lease, owner, now, expires);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not acquire lease " + lease + " in actor_lease", e);
        }
    }

    private static boolean take(Connection connection, String lease, String owner, Timestamp now, Timestamp expires)
            throws SQLException {
        try (PreparedStatement take = connection.prepareStatement(TAKE)) {
            take.setString(1, owner);
            take.setTimestamp(2, expires);
            take.setString(3, lease);
            take.setString(4, owner);
            take.setTimestamp(5, now);
            return take.executeUpdate() == 1;
        }
    }

    @Override
    public boolean renew(String lease, String owner, Duration ttl) {
        try (Connection connection = dataSource.getConnection()) {
            Timestamp now = Jdbc.now(connection);
            try (PreparedStatement renew = connection.prepareStatement(RENEW)) {
                renew.setTimestamp(1, plus(now, ttl));
                renew.setString(2, lease);
                renew.setString(3, owner);
                renew.setTimestamp(4, now);
                return renew.executeUpdate() == 1;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not renew lease " + lease + " in actor_lease", e);
        }
    }

    @Override
    public void release(String lease, String owner) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement release = connection.prepareStatement(RELEASE)) {
            release.setString(1, lease);
            release.setString(2, owner);
            release.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not release lease " + lease + " in actor_lease", e);
        }
    }

    @Override
    public Optional<String> holder(String lease) {
        try (Connection connection = dataSource.getConnection()) {
            Timestamp now = Jdbc.now(connection);
            try (PreparedStatement holder = connection.prepareStatement(HOLDER)) {
                holder.setString(1, lease);
                holder.setTimestamp(2, now);
                try (ResultSet row = holder.executeQuery()) {
                    return row.next() ? Optional.of(row.getString(1)) : Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read lease " + lease + " from actor_lease", e);
        }
    }

    private static Timestamp plus(Timestamp time, Duration duration) {
        return Timestamp.from(time.toInstant().plus(duration));
    }
}
