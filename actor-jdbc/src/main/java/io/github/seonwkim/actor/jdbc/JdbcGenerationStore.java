package io.github.seonwkim.actor.jdbc;

import io.github.seonwkim.actor.generation.ActivationResult;
import io.github.seonwkim.actor.generation.ActivationResult.Outcome;
import io.github.seonwkim.actor.generation.ActiveGeneration;
import io.github.seonwkim.actor.spi.GenerationStore;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.lang.Nullable;

/**
 * A {@link GenerationStore} in the {@code actor_generation} table of the application's {@link DataSource}:
 * one row per activation, keyed by deployment and epoch, so the newest row is the active generation and
 * the rows before it are the history. The compare-and-set is the primary key: an activation inserts
 * the row for {@code expectedEpoch + 1} after checking that {@code expectedEpoch} is the newest, and of
 * several concurrent inserts of the same epoch the database accepts exactly one. A unique key on the
 * operation ID makes a retried operation find the row it inserted. {@code activated_at} is the
 * database's clock, in epoch milliseconds. The table is created from {@link JdbcPeerRegistry#SCHEMA_LOCATION}.
 */
public final class JdbcGenerationStore implements GenerationStore {

    private static final String COLUMNS = "color, epoch, operation_id, activated_at";
    private static final String NEWEST = "SELECT " + COLUMNS + " FROM actor_generation WHERE deployment = ?"
            + " AND epoch = (SELECT MAX(epoch) FROM actor_generation WHERE deployment = ?)";
    private static final String BY_OPERATION =
            "SELECT " + COLUMNS + " FROM actor_generation WHERE deployment = ? AND operation_id = ?";
    private static final String INSERT =
            "INSERT INTO actor_generation (deployment, " + COLUMNS + ") VALUES (?, ?, ?, ?, ?)";

    private final DataSource dataSource;

    public JdbcGenerationStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<ActiveGeneration> active(String deployment) {
        try (Connection connection = dataSource.getConnection()) {
            return Optional.ofNullable(newest(connection, deployment));
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read the active generation of " + deployment, e);
        }
    }

    @Override
    public ActivationResult activate(String deployment, String color, long expectedEpoch, String operationId) {
        try (Connection connection = dataSource.getConnection()) {
            ActivationResult done = byOperation(connection, deployment, color, expectedEpoch, operationId);
            if (done != null) {
                return done;
            }
            ActiveGeneration current = newest(connection, deployment);
            if ((current == null ? 0 : current.epoch()) != expectedEpoch) {
                return new ActivationResult(Outcome.EPOCH_MISMATCH, current);
            }
            ActiveGeneration next = new ActiveGeneration(
                    color,
                    expectedEpoch + 1,
                    operationId,
                    Instant.ofEpochMilli(Jdbc.now(connection).getTime()));
            try (PreparedStatement insert = connection.prepareStatement(INSERT)) {
                insert.setString(1, deployment);
                insert.setString(2, color);
                insert.setLong(3, next.epoch());
                insert.setString(4, operationId);
                insert.setLong(5, next.activatedAt().toEpochMilli());
                insert.executeUpdate();
                return new ActivationResult(Outcome.ACTIVATED, next);
            } catch (SQLException e) {
                if (!Jdbc.isDuplicateKey(e)) {
                    throw e;
                }
                // Another activation took this epoch first, or the same operation did (a concurrent retry).
                done = byOperation(connection, deployment, color, expectedEpoch, operationId);
                return done != null
                        ? done
                        : new ActivationResult(Outcome.EPOCH_MISMATCH, newest(connection, deployment));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not activate " + color + " for " + deployment, e);
        }
    }

    /** What {@code operationId} activated before, as a retry or a conflict; null if it activated nothing. */
    @Nullable private static ActivationResult byOperation(
            Connection connection, String deployment, String color, long expectedEpoch, String operationId)
            throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(BY_OPERATION)) {
            select.setString(1, deployment);
            select.setString(2, operationId);
            try (ResultSet row = select.executeQuery()) {
                if (!row.next()) {
                    return null;
                }
                ActiveGeneration done = generation(row);
                boolean same = done.color().equals(color) && done.epoch() == expectedEpoch + 1;
                return new ActivationResult(same ? Outcome.ALREADY_ACTIVATED : Outcome.OPERATION_CONFLICT, done);
            }
        }
    }

    @Nullable private static ActiveGeneration newest(Connection connection, String deployment) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(NEWEST)) {
            select.setString(1, deployment);
            select.setString(2, deployment);
            try (ResultSet row = select.executeQuery()) {
                return row.next() ? generation(row) : null;
            }
        }
    }

    private static ActiveGeneration generation(ResultSet row) throws SQLException {
        return new ActiveGeneration(
                row.getString(1), row.getLong(2), row.getString(3), Instant.ofEpochMilli(row.getLong(4)));
    }
}
