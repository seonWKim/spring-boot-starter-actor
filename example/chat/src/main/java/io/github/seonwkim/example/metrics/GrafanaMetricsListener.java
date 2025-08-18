package io.github.seonwkim.example.metrics;

import io.github.seonwkim.metrics.listener.ActorSystemEventListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom SystemMetricsListener implementation that exports actor system metrics
 * to Prometheus/Grafana via Micrometer.
 */
@Component
public class GrafanaMetricsListener implements ActorSystemEventListener.ActorLifecycleEventListener {
    private static final Logger logger = LoggerFactory.getLogger(GrafanaMetricsListener.class);
    
    private final MeterRegistry meterRegistry;
    
    // Metrics counters and gauges
    private final AtomicLong activeActors = new AtomicLong(0);
    private final AtomicLong totalActorsCreated = new AtomicLong(0);
    private final AtomicLong totalActorsTerminated = new AtomicLong(0);
    private final AtomicLong totalCellReplacements = new AtomicLong(0);
    
    // Micrometer counters
    private Counter actorCreatedCounter;
    private Counter actorTerminatedCounter;
    private Counter cellReplacementCounter;
    
    public GrafanaMetricsListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }
    
    @PostConstruct
    public void register() {
        ActorSystemEventListener.register(this);
        logger.info("GrafanaMetricsListener registered for actor system events");
    }
    
    @PreDestroy
    public void unregister() {
        ActorSystemEventListener.unregister(this);
        logger.info("GrafanaMetricsListener unregistered from actor system events");
    }
    
    private void initializeMetrics() {
        // Create gauge for active actors count
        Gauge.builder("actor.system.active", activeActors, AtomicLong::get)
            .description("Number of currently active actors in the system")
            .tags("type", "user")
            .register(meterRegistry);
        
        // Create counter for total actors created
        actorCreatedCounter = Counter.builder("actor.system.created.total")
            .description("Total number of actors created")
            .tags("type", "user")
            .register(meterRegistry);
        
        // Create counter for total actors terminated
        actorTerminatedCounter = Counter.builder("actor.system.terminated.total")
            .description("Total number of actors terminated")
            .tags("type", "user")
            .register(meterRegistry);
        
        // Create counter for cell replacements
        cellReplacementCounter = Counter.builder("actor.system.cell.replacements.total")
            .description("Total number of unstarted cell replacements")
            .register(meterRegistry);
        
        // Create gauge for total actors created (for rate calculations)
        Gauge.builder("actor.system.created.cumulative", totalActorsCreated, AtomicLong::get)
            .description("Cumulative count of actors created")
            .tags("type", "user")
            .register(meterRegistry);
        
        // Create gauge for total actors terminated (for rate calculations)
        Gauge.builder("actor.system.terminated.cumulative", totalActorsTerminated, AtomicLong::get)
            .description("Cumulative count of actors terminated")
            .tags("type", "user")
            .register(meterRegistry);
        
        // Create gauge for cell replacement rate
        Gauge.builder("actor.system.cell.replacements.cumulative", totalCellReplacements, AtomicLong::get)
            .description("Cumulative count of cell replacements")
            .register(meterRegistry);
    }
    
    @Override
    public void onActorCreated(Object actorCell) {
        try {
            if (!isTemporaryActor(actorCell)) {
                String actorPath = getActorPath(actorCell);
                long current = activeActors.incrementAndGet();
                totalActorsCreated.incrementAndGet();
                actorCreatedCounter.increment();
                
                logger.debug("Actor created: {}, active actors: {}", actorPath, current);
                
                // Log specific actor types for monitoring
                if (actorPath.contains("/user/")) {
                    logActorTypeMetric(actorPath, "created");
                }
            }
        } catch (Exception e) {
            logger.error("Error in onActorCreated", e);
        }
    }
    
    @Override
    public void onActorTerminated(Object actorCell) {
        try {
            if (!isTemporaryActor(actorCell)) {
                String actorPath = getActorPath(actorCell);
                long current = activeActors.decrementAndGet();
                totalActorsTerminated.incrementAndGet();
                actorTerminatedCounter.increment();
                
                logger.debug("Actor terminated: {}, active actors: {}", actorPath, current);
                
                // Log specific actor types for monitoring
                if (actorPath.contains("/user/")) {
                    logActorTypeMetric(actorPath, "terminated");
                }
            }
        } catch (Exception e) {
            logger.error("Error in onActorTerminated", e);
        }
    }
    
    @Override
    public void onUnstartedCellReplaced(Object unstartedCell, Object newCell) {
        try {
            totalCellReplacements.incrementAndGet();
            cellReplacementCounter.increment();
            
            if (!isTemporaryActor(newCell)) {
                String actorPath = getActorPath(newCell);
                logger.debug("UnstartedCell replaced for actor: {}", actorPath);
            }
        } catch (Exception e) {
            logger.error("Error in onUnstartedCellReplaced", e);
        }
    }
    
    private boolean isTemporaryActor(Object cell) {
        try {
            String pathString = getActorPath(cell);
            
            // Filter out system and temporary actors
            boolean isTemp = pathString.contains("/system/") || 
                           pathString.contains("/temp/") ||
                           pathString.contains("/stream") ||
                           pathString.contains("$");
            
            return isTemp;
        } catch (Exception e) {
            logger.error("Error checking if actor is temporary", e);
            return false;
        }
    }
    
    private String getActorPath(Object cell) throws Exception {
        Object self = cell.getClass().getMethod("self").invoke(cell);
        Object path = self.getClass().getMethod("path").invoke(self);
        return path.toString();
    }
    
    private void logActorTypeMetric(String actorPath, String event) {
        try {
            // Extract actor type from path
            String actorType = extractActorType(actorPath);
            
            // Create dynamic metric for specific actor types
            Counter.builder("actor.system." + event + ".by.type")
                .description("Actor " + event + " by type")
                .tags("actor_type", actorType)
                .register(meterRegistry)
                .increment();
        } catch (Exception e) {
            logger.debug("Could not log actor type metric for path: {}", actorPath);
        }
    }
    
    private String extractActorType(String actorPath) {
        // Extract the actor type from the path
        // Example: "pekko://application/user/HelloActor-123" -> "HelloActor"
        String[] parts = actorPath.split("/");
        if (parts.length > 0) {
            String lastPart = parts[parts.length - 1];
            // Remove any instance identifiers (numbers, UUIDs, etc.)
            return lastPart.replaceAll("-.*", "").replaceAll("\\d+$", "");
        }
        return "unknown";
    }
}
