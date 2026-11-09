package io.github.seonwkim.actor.autoconfigure.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.EnableActorSupport;
import io.github.seonwkim.actor.SpringActor;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorSystem;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** An HTTP request that asks an actor is one trace, and the actor's logs carry its trace id. */
@SpringBootTest(
        classes = TracingAcrossAskTest.App.class,
        properties = {"management.tracing.sampling.probability=1.0", "spring.actor.shutdown.exit-process=false"})
@AutoConfigureMockMvc
@AutoConfigureObservability(metrics = false)
class TracingAcrossAskTest {

    static final InMemorySpanExporter SPANS = InMemorySpanExporter.create();

    /** Replies with the trace id in its MDC, as its log lines would show it. */
    static class TraceIdActor implements SpringActor<TraceIdActor.Command> {
        public interface Command {}

        public static class WhichTrace extends AskCommand<String> implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext ctx) {
            return SpringActorBehavior.builder(Command.class, ctx)
                    .onMessage(WhichTrace.class, (context, msg) -> {
                        msg.reply(String.valueOf(MDC.get("traceId")));
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }

    @RestController
    static class TraceController {
        private final SpringActorSystem actorSystem;

        TraceController(SpringActorSystem actorSystem) {
            this.actorSystem = actorSystem;
        }

        @GetMapping("/trace")
        String trace() throws Exception {
            SpringActorHandle<TraceIdActor.Command> actor = actorSystem
                    .actor(TraceIdActor.class)
                    .withId("tracer")
                    .getOrSpawn()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
            // Asked from the request's thread, where the request's observation is current.
            return actor.ask(new TraceIdActor.WhichTrace())
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EnableActorSupport
    @Import({TraceIdActor.class, TraceController.class})
    static class App {
        /** Exports each span as it ends, to {@link #SPANS}. */
        @Bean
        SpanProcessor inMemorySpans() {
            return SimpleSpanProcessor.create(SPANS);
        }
    }

    @Autowired
    MockMvc mvc;

    @BeforeEach
    void clearSpans() {
        SPANS.reset();
    }

    @Test
    void anHttpRequestThatAsksAnActorIsOneTrace() throws Exception {
        MvcResult result = mvc.perform(get("/trace")).andExpect(status().isOk()).andReturn();
        String traceIdInActor = result.getResponse().getContentAsString();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(names(SPANS.getFinishedSpanItems()))
                .contains("http get /trace", "ask WhichTrace", "receive WhichTrace"));
        List<SpanData> spans = SPANS.getFinishedSpanItems();
        SpanData http = span(spans, "http get /trace");
        SpanData ask = span(spans, "ask WhichTrace");
        SpanData receive = span(spans, "receive WhichTrace");

        assertThat(traceIdInActor).isEqualTo(http.getTraceId());
        assertThat(ask.getTraceId()).isEqualTo(http.getTraceId());
        assertThat(receive.getTraceId()).isEqualTo(http.getTraceId());
        assertThat(receive.getParentSpanId()).isEqualTo(ask.getSpanId());
    }

    private static List<String> names(List<SpanData> spans) {
        return spans.stream().map(SpanData::getName).toList();
    }

    private static SpanData span(List<SpanData> spans, String name) {
        return spans.stream()
                .filter(span -> span.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
