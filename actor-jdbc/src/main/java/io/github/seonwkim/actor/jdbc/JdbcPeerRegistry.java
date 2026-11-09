package io.github.seonwkim.actor.jdbc;

import io.github.seonwkim.actor.spi.Peer;
import io.github.seonwkim.actor.spi.PeerRegistry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/**
 * A {@link PeerRegistry} in the {@code actor_peer} table of the application's {@link DataSource}.
 * Times come from the database's clock ({@code CURRENT_TIMESTAMP}), so the nodes' clocks need not
 * agree. The statements are plain SQL that H2, PostgreSQL and MySQL all run; the table is created
 * from {@link #SCHEMA_LOCATION}, which also creates the {@code actor_lease} table of {@link JdbcLeaseStore}.
 *
 * <p>A node that crashes leaves its row behind. {@link #peers} ignores it once it is older than the
 * ttl; a node that leaves gracefully deletes its row.
 */
public final class JdbcPeerRegistry implements PeerRegistry {

    /** The module's schema files (actor_peer and actor_lease), one per database ({@code h2}, {@code postgresql}, {@code mysql}, {@code mariadb}). */
    public static final String SCHEMA_LOCATION = "classpath:io/github/seonwkim/actor/jdbc/schema-@@platform@@.sql";

    private static final String UPDATE = "UPDATE actor_peer SET host = ?, port = ?, last_seen = CURRENT_TIMESTAMP"
            + " WHERE service_name = ? AND node_id = ?";
    private static final String INSERT = "INSERT INTO actor_peer (host, port, last_seen, service_name, node_id)"
            + " VALUES (?, ?, CURRENT_TIMESTAMP, ?, ?)";
    private static final String DELETE = "DELETE FROM actor_peer WHERE service_name = ? AND node_id = ?";
    private static final String SELECT =
            "SELECT node_id, host, port FROM actor_peer WHERE service_name = ? AND last_seen >= ?";

    private final DataSource dataSource;

    public JdbcPeerRegistry(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void register(Peer peer) {
        try (Connection connection = dataSource.getConnection()) {
            if (write(connection, UPDATE, peer) > 0) {
                return;
            }
            try {
                write(connection, INSERT, peer);
            } catch (SQLException e) {
                // Another writer inserted the same row since our update: update it instead.
                if (!Jdbc.isDuplicateKey(e)) {
                    throw e;
                }
                write(connection, UPDATE, peer);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not register " + peer + " in actor_peer", e);
        }
    }

    private static int write(Connection connection, String sql, Peer peer) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, peer.host());
            statement.setInt(2, peer.port());
            statement.setString(3, peer.service());
            statement.setString(4, peer.id());
            return statement.executeUpdate();
        }
    }

    @Override
    public void deregister(Peer peer) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setString(1, peer.service());
            statement.setString(2, peer.id());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not deregister " + peer + " from actor_peer", e);
        }
    }

    @Override
    public List<Peer> peers(String service, Duration ttl) {
        try (Connection connection = dataSource.getConnection()) {
            Timestamp oldest = Timestamp.from(Jdbc.now(connection).toInstant().minus(ttl));
            try (PreparedStatement statement = connection.prepareStatement(SELECT)) {
                statement.setString(1, service);
                statement.setTimestamp(2, oldest);
                try (ResultSet rows = statement.executeQuery()) {
                    List<Peer> peers = new ArrayList<>();
                    while (rows.next()) {
                        peers.add(new Peer(service, rows.getString(1), rows.getString(2), rows.getInt(3)));
                    }
                    return peers;
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read the peers of " + service + " from actor_peer", e);
        }
    }
}
