package io.github.seonwkim.core.pubsub;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import io.github.seonwkim.core.SpringActorSystem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = SpringTopicBasicTest.TestApp.class)
@TestPropertySource(
        properties = {
            "spring.actor.pekko.loglevel=INFO",
            "spring.actor.pekko.actor.provider=local"
        })
class SpringTopicBasicTest {

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.core")
    static class TestApp {}

    @Autowired private SpringActorSystem actorSystem;

    @Test
    void canCreateTopicWithBuilder() {
        // Create a topic using the builder
        SpringTopicRef<String> topic =
                actorSystem.topic(String.class).withName("test-topic").getOrCreate();

        assertThat(topic).isNotNull();
        assertThat(topic.getTopicName()).isEqualTo("test-topic");
    }

    @Test
    void getOrCreateIsIdempotent() {
        // Create the same topic multiple times
        SpringTopicRef<String> topic1 =
                actorSystem.topic(String.class).withName("idempotent-topic").getOrCreate();
        SpringTopicRef<String> topic2 =
                actorSystem.topic(String.class).withName("idempotent-topic").getOrCreate();

        assertThat(topic1).isNotNull();
        assertThat(topic2).isNotNull();
        assertThat(topic1.getTopicName()).isEqualTo(topic2.getTopicName());
    }

    @Test
    void topicsWithDifferentNamesAreDistinct() {
        // Create two different topics
        SpringTopicRef<String> topic1 =
                actorSystem.topic(String.class).withName("topic-1").getOrCreate();
        SpringTopicRef<String> topic2 =
                actorSystem.topic(String.class).withName("topic-2").getOrCreate();

        assertThat(topic1.getTopicName()).isEqualTo("topic-1");
        assertThat(topic2.getTopicName()).isEqualTo("topic-2");
        assertThat(topic1.getTopicName()).isNotEqualTo(topic2.getTopicName());
    }

    @Test
    void getMethodWorksSameAsGetOrCreate() {
        // get() should work the same as getOrCreate()
        SpringTopicRef<String> topic1 =
                actorSystem.topic(String.class).withName("get-test-topic").get();
        SpringTopicRef<String> topic2 =
                actorSystem.topic(String.class).withName("get-test-topic").getOrCreate();

        assertThat(topic1).isNotNull();
        assertThat(topic2).isNotNull();
        assertThat(topic1.getTopicName()).isEqualTo(topic2.getTopicName());
    }

    @Test
    void throwsExceptionWhenTopicNameNotSet() {
        // Try to create topic without setting name
        assertThrows(
                IllegalStateException.class, () -> actorSystem.topic(String.class).getOrCreate());
    }

    @Test
    void throwsExceptionWhenSubscriberIsNull() {
        SpringTopicRef<String> topic =
                actorSystem.topic(String.class).withName("null-subscriber-topic").getOrCreate();

        assertThrows(IllegalArgumentException.class, () -> topic.subscribe(null));
    }

    @Test
    void throwsExceptionWhenUnsubscriberIsNull() {
        SpringTopicRef<String> topic =
                actorSystem.topic(String.class).withName("null-unsubscriber-topic").getOrCreate();

        assertThrows(IllegalArgumentException.class, () -> topic.unsubscribe(null));
    }

    @Test
    void throwsExceptionWhenPublishingNull() {
        SpringTopicRef<String> topic =
                actorSystem.topic(String.class).withName("null-publish-topic").getOrCreate();

        assertThrows(IllegalArgumentException.class, () -> topic.publish(null));
    }
}
