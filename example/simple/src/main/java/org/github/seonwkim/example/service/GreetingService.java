package org.github.seonwkim.example.service;

import org.github.seonwkim.core.ActorRefWrapper;
import org.github.seonwkim.core.service.ActorLocalService;
import org.github.seonwkim.core.behaviors.impl.ActorCreationBehavior;
import org.springframework.stereotype.Service;

@Service
public class GreetingService {

    private final ActorLocalService service;

    public GreetingService(ActorLocalService service) {this.service = service;}

    public void hello() {
        ActorRefWrapper<ActorCreationBehavior> actorRefWrapper = service.getActorRefWrapper(
                ActorCreationBehavior.BEAN_NAME, ActorRefWrapper.class);
    }
}
