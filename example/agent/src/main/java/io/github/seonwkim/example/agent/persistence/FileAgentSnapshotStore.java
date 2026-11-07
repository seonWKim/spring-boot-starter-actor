package io.github.seonwkim.example.agent.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.seonwkim.example.agent.AgentProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** One JSON file per agent, replaced atomically on each save. */
@Component
public class FileAgentSnapshotStore implements AgentSnapshotStore {

    private final Path directory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FileAgentSnapshotStore(AgentProperties properties) {
        this.directory = Paths.get(properties.getSnapshotDir());
    }

    @Override
    public Optional<AgentSnapshot> load(String agentId) {
        Path file = fileFor(agentId);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(file.toFile(), AgentSnapshot.class));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read snapshot of agent " + agentId, e);
        }
    }

    @Override
    public void save(AgentSnapshot snapshot) {
        try {
            Files.createDirectories(directory);
            Path file = fileFor(snapshot.agentId());
            Path temp = Files.createTempFile(directory, "snapshot", ".tmp");
            Files.write(temp, objectMapper.writeValueAsString(snapshot).getBytes(StandardCharsets.UTF_8));
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write snapshot of agent " + snapshot.agentId(), e);
        }
    }

    private Path fileFor(String agentId) {
        return directory.resolve(agentId.replaceAll("[^A-Za-z0-9._-]", "_") + ".json");
    }
}
