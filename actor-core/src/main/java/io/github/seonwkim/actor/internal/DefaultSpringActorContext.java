package io.github.seonwkim.actor.internal;

import io.github.seonwkim.actor.SpringActorContext;
import org.springframework.util.ObjectUtils;

public class DefaultSpringActorContext extends SpringActorContext {

    private final String id;

    public DefaultSpringActorContext(String id) {
        if (id == null || ObjectUtils.isEmpty(id)) {
            throw new IllegalArgumentException("id must not be null or blank");
        }
        this.id = id;
    }

    @Override
    public String actorId() {
        return id;
    }
}
