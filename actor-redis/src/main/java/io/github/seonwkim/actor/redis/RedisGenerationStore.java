package io.github.seonwkim.actor.redis;

import io.github.seonwkim.actor.generation.ActivationResult;
import io.github.seonwkim.actor.generation.ActivationResult.Outcome;
import io.github.seonwkim.actor.generation.ActiveGeneration;
import io.github.seonwkim.actor.spi.GenerationStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * A {@link GenerationStore} in Redis. Each deployment has two hashes: {@code actor:generation:{<name>}}
 * holds the active generation (fields {@code color}, {@code epoch}, {@code operationId},
 * {@code activatedAt}), and {@code actor:generation:{<name>}:ops} maps each operation ID that activated
 * a generation to that generation. One Lua script checks the operation ID and the epoch and writes both
 * hashes, so Redis runs the compare-and-set as one step. The braces put both keys in one hash slot for
 * Redis Cluster. {@code activatedAt} is Redis's clock ({@code TIME}), in epoch milliseconds.
 *
 * <p><b>Failover risk.</b> Redis replicates asynchronously: an activation that the primary accepted just
 * before it failed can be lost, and the epoch it gave out can be given out again. Use a database
 * ({@code JdbcGenerationStore}) where that window is not acceptable.
 */
public final class RedisGenerationStore implements GenerationStore {

    /** Returns {outcome, color, epoch, operationId, activatedAt}, or {outcome} when there is no generation. */
    private static final RedisScript<List> ACTIVATE = RedisScript.of(
            """
            local done = redis.call('HGET', KEYS[2], ARGV[3])
            if done then
              local g = cjson.decode(done)
              local outcome = 'OPERATION_CONFLICT'
              if g.color == ARGV[1] and g.epoch == tonumber(ARGV[2]) + 1 then outcome = 'ALREADY_ACTIVATED' end
              return {outcome, g.color, tostring(g.epoch), ARGV[3], tostring(g.at)}
            end
            local current = redis.call('HMGET', KEYS[1], 'color', 'epoch', 'operationId', 'activatedAt')
            if tonumber(current[2] or '0') ~= tonumber(ARGV[2]) then
              if current[1] then return {'EPOCH_MISMATCH', current[1], current[2], current[3], current[4]} end
              return {'EPOCH_MISMATCH'}
            end
            local time = redis.call('TIME')
            local at = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            local epoch = tonumber(ARGV[2]) + 1
            redis.call('HSET', KEYS[1], 'color', ARGV[1], 'epoch', epoch, 'operationId', ARGV[3], 'activatedAt', at)
            redis.call('HSET', KEYS[2], ARGV[3], cjson.encode({color = ARGV[1], epoch = epoch, at = at}))
            return {'ACTIVATED', ARGV[1], tostring(epoch), ARGV[3], tostring(at)}
            """,
            List.class);

    private final StringRedisTemplate redis;

    public RedisGenerationStore(RedisConnectionFactory connectionFactory) {
        this.redis = new StringRedisTemplate(connectionFactory);
    }

    @Override
    public Optional<ActiveGeneration> active(String deployment) {
        List<Object> fields =
                redis.opsForHash().multiGet(key(deployment), List.of("color", "epoch", "operationId", "activatedAt"));
        return fields.get(0) == null
                ? Optional.empty()
                : Optional.of(
                        generation((String) fields.get(0), (String) fields.get(1), (String) fields.get(2), (String)
                                fields.get(3)));
    }

    @Override
    @SuppressWarnings("unchecked")
    public ActivationResult activate(String deployment, String color, long expectedEpoch, String operationId) {
        List<String> reply = redis.execute(
                ACTIVATE,
                List.of(key(deployment), key(deployment) + ":ops"),
                color,
                String.valueOf(expectedEpoch),
                operationId);
        Outcome outcome = Outcome.valueOf(reply.get(0));
        return new ActivationResult(
                outcome, reply.size() == 1 ? null : generation(reply.get(1), reply.get(2), reply.get(3), reply.get(4)));
    }

    private static String key(String deployment) {
        return "actor:generation:{" + deployment + "}";
    }

    private static ActiveGeneration generation(String color, String epoch, String operationId, String activatedAt) {
        return new ActiveGeneration(
                color, Long.parseLong(epoch), operationId, Instant.ofEpochMilli(Long.parseLong(activatedAt)));
    }
}
