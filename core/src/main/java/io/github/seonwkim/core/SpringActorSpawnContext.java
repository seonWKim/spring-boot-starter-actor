package io.github.seonwkim.core;

import java.time.Duration;

import org.apache.pekko.actor.typed.MailboxSelector;

import io.github.seonwkim.core.impl.DefaultSpringActorContext;

public class SpringActorSpawnContext<A extends SpringActor<A, C>, C> {
    private final Class<A> actorClass;
    private final Class<C> commandClass;
    private final SpringActorContext actorContext;
    private final Duration duration;
    private final MailboxSelector mailboxSelector;
    private final boolean isClusterSingleton;

    public SpringActorSpawnContext(
            Class<A> actorClass,
            Class<C> commandClass,
            SpringActorContext actorContext,
            Duration duration,
            MailboxSelector mailboxSelector,
            boolean isClusterSingleton) {
        this.actorClass = actorClass;
        this.commandClass = commandClass;
        this.actorContext = actorContext;
        this.duration = duration;
        this.mailboxSelector = mailboxSelector;
        this.isClusterSingleton = isClusterSingleton;
    }

    public Class<A> getActorClass() {
        return actorClass;
    }

    public Class<C> getCommandClass() {
        return commandClass;
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
        private Class<C> commandClass;
        private SpringActorContext actorContext;
        private Duration duration = Duration.ofSeconds(3);
        private MailboxSelector mailboxSelector = MailboxSelector.defaultMailbox();
        private boolean isClusterSingleton = false;

        public Class<A> getActorClass() {
            return actorClass;
        }

        public Builder<A, C> commandClass(Class<C> commandClass) {
            this.commandClass = commandClass;
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
            if (actorClass == null || commandClass == null || actorContext == null) {
                throw new IllegalArgumentException("actorClass, commandClass and actorContext must be set");
            }
            return new SpringActorSpawnContext<>(
                    actorClass,
                    commandClass,
                    actorContext,
                    duration,
                    mailboxSelector,
                    isClusterSingleton
            );
        }
    }
}
