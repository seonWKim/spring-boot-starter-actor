package io.github.seonwkim.actor.mixedversion;

/**
 * Version 1 of a message. {@link MixedVersionMessageTest} compiles version 2, which adds a field, and
 * runs one node on each.
 */
public record Hello(String text) implements Versioned {

    @Override
    public String describe() {
        return text;
    }
}
