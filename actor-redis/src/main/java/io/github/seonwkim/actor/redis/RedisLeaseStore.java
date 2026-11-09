package io.github.seonwkim.actor.redis;

import io.github.seonwkim.actor.spi.LeaseStore;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * A {@link LeaseStore} in Redis: one key per lease, {@code actor:lease:<name>}, whose value is the
 * holder and whose expiry is the ttl, on Redis's clock. A free lease is taken with
 * {@code SET NX PX}; renewing and releasing compare the holder and change the key in one Lua script,
 * so only the holder can do either.
 *
 * <p><b>Failover risk.</b> Redis replicates asynchronously. If the primary fails after granting a
 * lease and before a replica received it, the promoted replica has no lease and grants it again:
 * two owners hold it until the first one's next renewal fails. Use leases on Redis only where that
 * window is acceptable, or use a database ({@code JdbcLeaseStore}) when it is not.
 */
public final class RedisLeaseStore implements LeaseStore {

    private static final String PREFIX = "actor:lease:";

    /** Extends the lease if the caller holds it. */
    private static final RedisScript<Long> RENEW = RedisScript.of(
            """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            return 0
            """,
            Long.class);

    /** Deletes the lease if the caller holds it. */
    private static final RedisScript<Long> RELEASE = RedisScript.of(
            """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """,
            Long.class);

    private final StringRedisTemplate redis;

    public RedisLeaseStore(RedisConnectionFactory connectionFactory) {
        this.redis = new StringRedisTemplate(connectionFactory);
    }

    @Override
    public boolean tryAcquire(String lease, String owner, Duration ttl) {
        if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(PREFIX + lease, owner, ttl))) {
            return true;
        }
        // Held already: by this owner, the acquisition renews it.
        return renew(lease, owner, ttl);
    }

    @Override
    public boolean renew(String lease, String owner, Duration ttl) {
        Long renewed = redis.execute(RENEW, List.of(PREFIX + lease), owner, String.valueOf(ttl.toMillis()));
        return renewed != null && renewed == 1;
    }

    @Override
    public void release(String lease, String owner) {
        redis.execute(RELEASE, List.of(PREFIX + lease), owner);
    }

    @Override
    public Optional<String> holder(String lease) {
        return Optional.ofNullable(redis.opsForValue().get(PREFIX + lease));
    }
}
