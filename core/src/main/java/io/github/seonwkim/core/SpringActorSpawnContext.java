package io.github.seonwkim.core;

import java.time.Duration;

import org.apache.pekko.actor.typed.MailboxSelector;

import io.github.seonwkim.core.impl.DefaultSpringActorContext;

public class SpringActorSpawnContext<A extends SpringActor<A, C>, C> {
    private final Class<A> actorClass;
    private final SpringActorContext actorContext;
    private final Duration duration;
    private final MailboxSelector mailboxSelector;
    private final boolean isClusterSingleton;

    public SpringActorSpawnContext(
            Class<A> actorClass,
            SpringActorContext actorContext,
            Duration duration,
            MailboxSelector mailboxSelector,
            boolean isClusterSingleton) {
        this.actorClass = actorClass;
        this.actorContext = actorContext;
        this.duration = duration;
        this.mailboxSelector = mailboxSelector;
        this.isClusterSingleton = isClusterSingleton;
    }

    public Class<A> getActorClass() {
        return actorClass;
    }

    public SpringActorContext getActorContext() {
        return actorContext;
    }

    public Duration getDuration() {
        return duration;
    }

    public MailboxSelector getMailboxSelector() {
        return mailboxSelector;
    }

    public boolean isClusterSingleton() {
        return isClusterSingleton;
    }

    public static class Builder<A extends SpringActor<A, C>, C> {
        private Class<A> actorClass;
        private SpringActorContext actorContext;
        private Duration duration = Duration.ofSeconds(3);
        private MailboxSelector mailboxSelector = MailboxSelector.defaultMailbox();
        private boolean isClusterSingleton = false;

        public Builder<A, C> actorClass(Class<A> actorClass) {
            this.actorClass = actorClass;
            return this;
        }

        public Builder<A, C> actorId(String actorId) {
            this.actorContext = new DefaultSpringActorContext(actorId);
            return this;
        }

        public Builder<A, C> actorContext(SpringActorContext actorContext) {
            this.actorContext = actorContext;
            return this;
        }

        public Builder<A, C> duration(Duration duration) {
            this.duration = duration;
            return this;
        }

        public Builder<A, C> mailboxSelector(MailboxSelector mailboxSelector) {
            this.mailboxSelector = mailboxSelector;
            return this;
        }

        public Builder<A, C> isClusterSingleton(boolean isClusterSingleton) {
            this.isClusterSingleton = isClusterSingleton;
            return this;
        }

        public SpringActorSpawnContext<A, C> build() {
            if (actorClass == null || actorContext == null) {
                throw new IllegalArgumentException("actorClass, commandClass and actorContext must be set");
            }
            return new SpringActorSpawnContext<>(
                    actorClass,
                    actorContext,
                    duration,
                    mailboxSelector,
                    isClusterSingleton
            );
        }
    }
}
