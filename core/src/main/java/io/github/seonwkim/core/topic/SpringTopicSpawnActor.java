package io.github.seonwkim.core.topic;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.seonwkim.core.AskCommand;
import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.serialization.JsonSerializable;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.pubsub.Topic;
import org.springframework.stereotype.Component;

/**
 * Centralized actor for managing topic creation and lifecycle.
 * All topics are created as children of this actor.
 */
@Component
public class SpringTopicSpawnActor implements SpringActor<SpringTopicSpawnActor.Command> {

    public interface Command extends JsonSerializable {}

    // Request to create a topic
    public static class CreateTopic<M> extends AskCommand<SpringTopicRef<M>> implements Command {
        public final Class<M> messageType;
        public final String topicName;

        @JsonCreator
        public CreateTopic(
                @JsonProperty("messageType") Class<M> messageType,
                @JsonProperty("topicName") String topicName) {
            this.messageType = messageType;
            this.topicName = topicName;
        }
    }

    // Request to get or create a topic (idempotent)
    public static class GetOrCreateTopic<M> extends AskCommand<SpringTopicRef<M>> implements Command {
        public final Class<M> messageType;
        public final String topicName;

        @JsonCreator
        public GetOrCreateTopic(
                @JsonProperty("messageType") Class<M> messageType,
                @JsonProperty("topicName") String topicName) {
            this.messageType = messageType;
            this.topicName = topicName;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
                .onMessage(CreateTopic.class, (ctx, msg) -> {
                    ActorRef<Topic.Command<Object>> topicActor = ctx.spawn(
                            Topic.create((Class<Object>) msg.messageType, msg.topicName),
                            msg.topicName
                    );
                    SpringTopicRef<Object> topicRef = new SpringTopicRef<>(topicActor, msg.topicName);
                    CreateTopic<Object> typedMsg = (CreateTopic<Object>) msg;
                    typedMsg.reply(topicRef);
                    return Behaviors.same();
                })
                .onMessage(GetOrCreateTopic.class, (ctx, msg) -> {
                    SpringTopicRef<Object> existing = ctx.getChild(msg.topicName)
                            .map(ref -> {
                                ActorRef<Topic.Command<Object>> typedRef =
                                        (ActorRef<Topic.Command<Object>>) (Object) ref;
                                return new SpringTopicRef<>(typedRef, msg.topicName);
                            })
                            .orElse(null);

                    SpringTopicRef<Object> topicRef;
                    if (existing != null) {
                        topicRef = existing;
                    } else {
                        ActorRef<Topic.Command<Object>> topicActor = ctx.spawn(
                                Topic.create((Class<Object>) msg.messageType, msg.topicName),
                                msg.topicName
                        );
                        topicRef = new SpringTopicRef<>(topicActor, msg.topicName);
                    }

                    GetOrCreateTopic<Object> typedMsg = (GetOrCreateTopic<Object>) msg;
                    typedMsg.reply(topicRef);
                    return Behaviors.same();
                })
                .build();
    }
}
