package io.github.seonwkim.example.agent.tool;

import org.springframework.stereotype.Component;

/** A read-only tool with a canned answer. */
@Component
public class WeatherTool implements Tool {

    @Override
    public String name() {
        return "weather";
    }

    @Override
    public String invoke(String city) {
        return "sunny and 21C in " + city;
    }
}
