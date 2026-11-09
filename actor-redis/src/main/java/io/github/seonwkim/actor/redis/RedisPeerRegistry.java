package io.github.seonwkim.actor.redis;

import io.github.seonwkim.actor.spi.Peer;
import io.github.seonwkim.actor.spi.PeerRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * A {@link PeerRegistry} in Redis: one hash per node under {@code actor:peer:<service>:<id>}, with
 * the time of its last renewal from Redis's own clock ({@code TIME}). Each key expires after the
 * entry ttl, so a crashed node's entry disappears by itself. {@link #peers} reads the keys with
 * {@code SCAN}, never {@code KEYS}. {@code SCAN} sees one server: use a single Redis or a
 * primary with replicas (Sentinel), not Redis Cluster.
 */
public final class RedisPeerRegistry implements PeerRegistry {

    private static final String PREFIX = "actor:peer:";

    /** Writes the entry with the server's time and sets its expiry, in one step. */
    private static final RedisScript<Long> REGISTER = RedisScript.of(
            """
            local time = redis.call('TIME')
            local seen = time[1] .. string.format('%03d', math.floor(time[2] / 1000))
            redis.call('HSET', KEYS[1], 'service', ARGV[1], 'id', ARGV[2], 'host', ARGV[3], 'port', ARGV[4], 'seen', seen)
            redis.call('PEXPIRE', KEYS[1], ARGV[5])
            return seen
            """,
            Long.class);

    private final StringRedisTemplate redis;
    private final Duration entryTtl;

    /**
     * @param connectionFactory the application's Redis connection
     * @param entryTtl how long a key lives after its last renewal; at least the ttl readers pass to
     *     {@link #peers}
     */
    public RedisPeerRegistry(RedisConnectionFactory connectionFactory, Duration entryTtl) {
        this.redis = new StringRedisTemplate(connectionFactory);
        this.entryTtl = entryTtl;
    }

    private static String key(Peer peer) {
        return PREFIX + peer.service() + ":" + peer.id();
    }

    @Override
    public void register(Peer peer) {
        redis.execute(
                REGISTER,
                List.of(key(peer)),
                peer.service(),
                peer.id(),
                peer.host(),
                String.valueOf(peer.port()),
                String.valueOf(entryTtl.toMillis()));
    }

    @Override
    public void deregister(Peer peer) {
        redis.delete(key(peer));
    }

    @Override
    public List<Peer> peers(String service, Duration ttl) {
        Long now = redis.execute(
                (RedisCallback<Long>) connection -> connection.serverCommands().time());
        long oldest = Objects.requireNonNull(now) - ttl.toMillis();
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(PREFIX + escapeGlob(service) + ":*")
                .count(500)
                .build();
        try (Cursor<String> cursor = redis.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }
        List<Peer> peers = new ArrayList<>();
        for (String key : keys) {
            Map<Object, Object> entry = redis.opsForHash().entries(key);
            // The pattern also matches services whose name continues after a ':'; keep ours only.
            if (!service.equals(entry.get("service"))
                    || Long.parseLong((String) entry.getOrDefault("seen", "0")) < oldest) {
                continue;
            }
            peers.add(new Peer(
                    service, field(entry, "id"), field(entry, "host"), Integer.parseInt(field(entry, "port"))));
        }
        return peers;
    }

    private static String field(Map<Object, Object> entry, String name) {
        return (String) Objects.requireNonNull(entry.get(name), name);
    }

    private static String escapeGlob(String text) {
        return text.replaceAll("([*?\\[\\]\\\\])", "\\\\$1");
    }
}
