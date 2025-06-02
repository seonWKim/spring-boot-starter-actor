package io.github.seonwkim.core;

import java.time.Duration;

import org.apache.pekko.actor.typed.MailboxSelector;

import io.github.seonwkim.core.impl.DefaultSpringActorContext;

public class SpringActorSpawnContext<T> {
    private final Class<T> commandClass;
    private final SpringActorContext actorContext;
    private final Duration duration;
    private final MailboxSelector mailboxSelector;
    private final boolean isClusterSingleton;

    public SpringActorSpawnContext(
            Class<T> commandClass,
            SpringActorContext actorContext,
            Duration duration,
            MailboxSelector mailboxSelector,
            boolean isClusterSingleton) {
        this.commandClass = commandClass;
        this.actorContext = actorContext;
        this.duration = duration;
        this.mailboxSelector = mailboxSelector;
        this.isClusterSingleton = isClusterSingleton;
    }

    public Class<T> getCommandClass() {
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

    public static class Builder<T> {
        private Class<T> commandClass;
        private SpringActorContext actorContext;
        private Duration duration = Duration.ofSeconds(3);
        private MailboxSelector mailboxSelector = MailboxSelector.defaultMailbox();
        private boolean isClusterSingleton = false;

        public Builder<T> commandClass(Class<T> commandClass) {
            this.commandClass = commandClass;
            return this;
        }

        public Builder<T> actorId(String actorId) {
            this.actorContext = new DefaultSpringActorContext(actorId);
            return this;
        }

        public Builder<T> actorContext(SpringActorContext actorContext) {
            this.actorContext = actorContext;
            return this;
        }

        public Builder<T> duration(Duration duration) {
            this.duration = duration;
            return this;
        }

        public Builder<T> mailboxSelector(MailboxSelector mailboxSelector) {
            this.mailboxSelector = mailboxSelector;
            return this;
        }

        public Builder<T> isClusterSingleton(boolean isClusterSingleton) {
            this.isClusterSingleton = isClusterSingleton;
            return this;
        }

        public SpringActorSpawnContext<T> build() {
            if (commandClass == null || actorContext == null) {
                throw new IllegalArgumentException("commandClass and actorContext must be set");
            }
            return new SpringActorSpawnContext<>(
                    commandClass,
                    actorContext,
                    duration,
                    mailboxSelector,
                    isClusterSingleton
            );
        }
    }
}
