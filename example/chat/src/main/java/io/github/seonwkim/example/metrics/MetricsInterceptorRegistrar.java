package io.github.seonwkim.example.metrics;

import io.github.seonwkim.metrics.impl.ComprehensiveInvokeAdviceInterceptor;
import io.github.seonwkim.metrics.impl.ComprehensiveLifecycleInterceptor;
import io.github.seonwkim.metrics.interceptor.ActorLifeCycleEventInterceptorsHolder;
import io.github.seonwkim.metrics.interceptor.InvokeAdviceEventInterceptorsHolder;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registers comprehensive metrics interceptors for actor system observability.
 * 
 * This component ensures that all actor lifecycle and message processing events
 * are captured and tracked in the ActorMetricsRegistry.
 */
@Component
public class MetricsInterceptorRegistrar {
    private static final Logger logger = LoggerFactory.getLogger(MetricsInterceptorRegistrar.class);

    private final ComprehensiveInvokeAdviceInterceptor invokeInterceptor;
    private final ComprehensiveLifecycleInterceptor lifecycleInterceptor;

    public MetricsInterceptorRegistrar() {
        this.invokeInterceptor = new ComprehensiveInvokeAdviceInterceptor();
        this.lifecycleInterceptor = new ComprehensiveLifecycleInterceptor();
    }

    @PostConstruct
    public void register() {
        InvokeAdviceEventInterceptorsHolder.register(invokeInterceptor);
        ActorLifeCycleEventInterceptorsHolder.register(lifecycleInterceptor);
        
        logger.info("Comprehensive metrics interceptors registered successfully");
        logger.info("Actor system metrics are now being collected");
    }

    @PreDestroy
    public void unregister() {
        InvokeAdviceEventInterceptorsHolder.unregister(invokeInterceptor);
        ActorLifeCycleEventInterceptorsHolder.unregister(lifecycleInterceptor);
        
        logger.info("Comprehensive metrics interceptors unregistered");
    }
}
