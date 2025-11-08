package io.github.seonwkim.core.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests for ErrorContext builder and functionality.
 */
public class ErrorContextTest {

    @Test
    public void testBuilderCreatesEmptyContext() {
        ErrorContext context = ErrorContext.builder().build();

        assertThat(context.getActorClass()).isNull();
        assertThat(context.getActorId()).isNull();
        assertThat(context.getActorPath()).isNull();
        assertThat(context.getMessageType()).isNull();
        assertThat(context.getActorState()).isNull();
        assertThat(context.getAdditionalInfo()).isEmpty();
    }

    @Test
    public void testBuilderSetsAllFields() {
        ErrorContext context =
                ErrorContext.builder()
                        .actorClass("MyActor")
                        .actorId("actor-123")
                        .actorPath("/user/my-actor")
                        .messageType("MyMessage")
                        .actorState("RUNNING")
                        .additionalInfo("key1", "value1")
                        .additionalInfo("key2", 42)
                        .build();

        assertThat(context.getActorClass()).isEqualTo("MyActor");
        assertThat(context.getActorId()).isEqualTo("actor-123");
        assertThat(context.getActorPath()).isEqualTo("/user/my-actor");
        assertThat(context.getMessageType()).isEqualTo("MyMessage");
        assertThat(context.getActorState()).isEqualTo("RUNNING");
        assertThat(context.getAdditionalInfo()).hasSize(2);
        assertThat(context.getAdditionalInfo("key1")).isEqualTo("value1");
        assertThat(context.getAdditionalInfo("key2")).isEqualTo(42);
    }

    @Test
    public void testBuilderFluentChaining() {
        ErrorContext context =
                ErrorContext.builder()
                        .actorClass("OrderActor")
                        .actorPath("/user/order-actor")
                        .messageType("CreateOrder")
                        .build();

        assertThat(context.getActorClass()).isEqualTo("OrderActor");
        assertThat(context.getActorPath()).isEqualTo("/user/order-actor");
        assertThat(context.getMessageType()).isEqualTo("CreateOrder");
    }

    @Test
    public void testGetAdditionalInfoReturnsNull() {
        ErrorContext context = ErrorContext.builder().build();

        assertThat(context.getAdditionalInfo("nonexistent")).isNull();
    }

    @Test
    public void testGetAdditionalInfoReturnsCopy() {
        ErrorContext context =
                ErrorContext.builder().additionalInfo("key", "value").build();

        // Get the map and try to modify it
        var info = context.getAdditionalInfo();
        info.put("newKey", "newValue");

        // Original context should not be affected
        assertThat(context.getAdditionalInfo()).hasSize(1);
        assertThat(context.getAdditionalInfo("newKey")).isNull();
    }

    @Test
    public void testAdditionalInfoWithDifferentTypes() {
        ErrorContext context =
                ErrorContext.builder()
                        .additionalInfo("string", "text")
                        .additionalInfo("integer", 123)
                        .additionalInfo("boolean", true)
                        .additionalInfo("long", 123L)
                        .build();

        assertThat(context.getAdditionalInfo("string")).isEqualTo("text");
        assertThat(context.getAdditionalInfo("integer")).isEqualTo(123);
        assertThat(context.getAdditionalInfo("boolean")).isEqualTo(true);
        assertThat(context.getAdditionalInfo("long")).isEqualTo(123L);
    }

    @Test
    public void testPartialContextCreation() {
        ErrorContext context =
                ErrorContext.builder().actorPath("/user/my-actor").messageType("MyMessage").build();

        assertThat(context.getActorPath()).isEqualTo("/user/my-actor");
        assertThat(context.getMessageType()).isEqualTo("MyMessage");
        assertThat(context.getActorClass()).isNull();
        assertThat(context.getActorId()).isNull();
        assertThat(context.getActorState()).isNull();
    }
}
