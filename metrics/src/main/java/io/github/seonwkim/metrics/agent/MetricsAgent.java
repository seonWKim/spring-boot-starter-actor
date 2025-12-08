package io.github.seonwkim.metrics.agent;

import io.github.seonwkim.metrics.core.MetricsRegistry;
import io.github.seonwkim.metrics.modules.actor.ActorLifecycleModule;
import io.github.seonwkim.metrics.modules.mailbox.MailboxModule;
import io.github.seonwkim.metrics.modules.message.MessageProcessingModule;
import java.io.File;
import java.lang.instrument.Instrumentation;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.jar.JarFile;
import javax.annotation.Nullable;
import net.bytebuddy.agent.builder.AgentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Java agent for bytecode instrumentation of Pekko actors.
 */
public class MetricsAgent {

    private static final Logger log = LoggerFactory.getLogger(MetricsAgent.class);
    private static final String ENV_ENABLED = "ACTOR_METRICS_ENABLED";
    private static final String ENV_MODULE_PREFIX = "ACTOR_METRICS_INSTRUMENT_";

    private static final Map<String, Function<AgentBuilder, AgentBuilder>> MODULES = new LinkedHashMap<>();

    static {
        MODULES.put("actor-lifecycle", ActorLifecycleModule::instrument);
        MODULES.put("message-processing", MessageProcessingModule::instrument);
        MODULES.put("mailbox", MailboxModule::instrument);
    }

    @Nullable private static volatile MetricsRegistry registry;

    public static void premain(String arguments, Instrumentation instrumentation) {
        if (!getBooleanEnv(ENV_ENABLED, true)) {
            log.info("Metrics disabled via {}", ENV_ENABLED);
            return;
        }

        try {
            addAgentToSystemClassLoader(instrumentation);
            installInstrumentation(instrumentation);
        } catch (Exception e) {
            log.error("Failed to initialize agent", e);
        }
    }

    private static void installInstrumentation(Instrumentation instrumentation) {
        AgentBuilder builder = new AgentBuilder.Default()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(AgentBuilder.TypeStrategy.Default.REDEFINE)
                .with(AgentBuilder.InitializationStrategy.NoOp.INSTANCE);

        for (Map.Entry<String, Function<AgentBuilder, AgentBuilder>> entry : MODULES.entrySet()) {
            String moduleId = entry.getKey();
            if (!isModuleEnabled(moduleId)) {
                log.info("Module disabled: {}", moduleId);
                continue;
            }

            try {
                builder = entry.getValue().apply(builder);
                log.info("Module enabled: {}", moduleId);
            } catch (Exception e) {
                log.warn("Failed to instrument module: {}", moduleId, e);
            }
        }

        builder.installOn(instrumentation);
        log.info("Agent installed");
    }

    private static void addAgentToSystemClassLoader(Instrumentation instrumentation) {
        try {
            var codeSource = MetricsAgent.class.getProtectionDomain().getCodeSource();
            if (codeSource == null) {
                log.warn("Could not determine agent JAR location");
                return;
            }

            File jar = new File(codeSource.getLocation().toURI());
            if (!jar.exists() || !jar.isFile()) {
                log.warn("Agent JAR not found: {}", jar);
                return;
            }

            instrumentation.appendToSystemClassLoaderSearch(new JarFile(jar));
            log.info("Added agent JAR to system classloader: {}", jar.getAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to add agent JAR to system classloader", e);
        }
    }

    private static boolean isModuleEnabled(String moduleId) {
        String key = ENV_MODULE_PREFIX + moduleId.toUpperCase().replace("-", "_");
        return getBooleanEnv(key, true);
    }

    private static boolean getBooleanEnv(String key, boolean defaultValue) {
        String value = System.getenv(key);
        if (value == null) {
            value = System.getProperty(key);
        }
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    public static void agentmain(String arguments, Instrumentation instrumentation) {
        premain(arguments, instrumentation);
    }

    @Nullable public static MetricsRegistry getRegistry() {
        return registry;
    }

    public static void setRegistry(@Nullable MetricsRegistry metricsRegistry) {
        registry = metricsRegistry;
        if (metricsRegistry != null) {
            metricsRegistry.registerModule(new ActorLifecycleModule());
            metricsRegistry.registerModule(new MessageProcessingModule());
            metricsRegistry.registerModule(new MailboxModule());
            log.info(
                    "Registry set: {} modules enabled",
                    metricsRegistry.getModules().size());
        } else {
            log.info("Registry cleared: metrics disabled");
        }
    }
}
