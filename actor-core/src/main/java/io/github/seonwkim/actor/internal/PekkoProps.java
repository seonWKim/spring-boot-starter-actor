package io.github.seonwkim.actor.internal;

import io.github.seonwkim.actor.DispatcherConfig;
import io.github.seonwkim.actor.MailboxConfig;
import io.github.seonwkim.actor.SupervisionStrategy;
import io.github.seonwkim.actor.TagsConfig;
import org.apache.pekko.actor.typed.ActorTags;
import org.apache.pekko.actor.typed.MailboxSelector;
import org.apache.pekko.actor.typed.Props;
import org.apache.pekko.actor.typed.SupervisorStrategy;

/** Maps the starter's spawn options to Pekko {@link Props} and {@link SupervisorStrategy}. */
public final class PekkoProps {

    private PekkoProps() {}

    /** Props carrying all three settings at once: each one is added, none replaces another. */
    public static Props of(DispatcherConfig dispatcher, MailboxConfig mailbox, TagsConfig tags) {
        Props props = Props.empty();
        props = switch (dispatcher) {
            case DispatcherConfig.Default d -> props;
            case DispatcherConfig.Blocking b -> props.withDispatcherFromConfig(
                    "pekko.actor.default-blocking-io-dispatcher");
            case DispatcherConfig.SameAsParent s -> props.withDispatcherSameAsParent();
            case DispatcherConfig.FromConfig f -> props.withDispatcherFromConfig(f.path());
        };
        props = switch (mailbox) {
            case MailboxConfig.Default d -> props;
            case MailboxConfig.Bounded b -> MailboxSelector.bounded(b.capacity())
                    .withNext(props);
            case MailboxConfig.FromConfig f -> props.withMailboxFromConfig(f.path());
        };
        return tags.isEmpty() ? props : ActorTags.create(tags.tags()).withNext(props);
    }

    public static SupervisorStrategy supervisor(SupervisionStrategy strategy) {
        return switch (strategy) {
            case SupervisionStrategy.Restart r -> r.maxRetries() < 0
                    ? SupervisorStrategy.restart()
                    : SupervisorStrategy.restart().withLimit(r.maxRetries(), r.within());
            case SupervisionStrategy.Resume r -> SupervisorStrategy.resume();
            case SupervisionStrategy.Stop s -> SupervisorStrategy.stop();
        };
    }
}
