package io.github.seonwkim.core.impl;

import io.github.seonwkim.core.SpringActorContext;

public class DefaultSpringActorContext extends SpringActorContext {

    private final String id;

    public DefaultSpringActorContext(String id) {
        this.id = id;
    }

    @Override
    public String actorId() {
        return id;
    }
}
