package io.github.seonwkim.example.agent.tool;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Every {@link Tool} bean, by name. */
@Component
public class ToolRegistry {

    private final Map<String, Tool> tools;

    public ToolRegistry(List<Tool> tools) {
        this.tools = tools.stream().collect(Collectors.toUnmodifiableMap(Tool::name, Function.identity()));
    }

    public Optional<Tool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }
}
