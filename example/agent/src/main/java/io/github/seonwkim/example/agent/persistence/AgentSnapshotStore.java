package io.github.seonwkim.example.agent.persistence;

import java.util.Optional;

// --8<-- [start:snapshot-store]
/**
 * Saves and loads agent snapshots. The example writes JSON files; a JPA implementation with an
 * {@code ActorSnapshot} entity and repository, as in example/persistence, plugs in the same way.
 */
public interface AgentSnapshotStore {

    Optional<AgentSnapshot> load(String agentId);

    void save(AgentSnapshot snapshot);
}
// --8<-- [end:snapshot-store]
