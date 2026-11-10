package io.github.seonwkim.actor.mixedversion;

import io.github.seonwkim.actor.serialization.JsonSerializable;

/** A message whose class differs between the nodes of {@link MixedVersionMessageTest}. */
public interface Versioned extends JsonSerializable {

    /** What the receiving node read: the fields of its own version of the class. */
    String describe();
}
