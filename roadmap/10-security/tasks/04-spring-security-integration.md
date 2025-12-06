# Task 2.1: Spring Security Integration

**Priority:** MEDIUM-HIGH
**Estimated Effort:** 3 weeks
**Dependencies:** Phase 1 (TLS/SSL) recommended but not required

---

## Overview

Integrate Spring Security framework with the actor system to enable authentication and authorization for actor operations. This includes security context propagation, support for @Secured annotations, and integration with Spring Security filters.

---

## Requirements

### 1. Security Context Propagation to Actors

Propagate Spring Security context across actor boundaries:

```java
@Slf4j
public class SecurityContextPropagator {
    
    /**
     * Wrap message with security context
     */
    public static <T> MessageWithContext<T> wrapWithContext(T message) {
        SecurityContext context = SecurityContextHolder.getContext();
        return new MessageWithContext<>(message, context);
    }
    
    /**
     * Execute with security context
     */
    public static <T> T executeWithContext(
        SecurityContext context,
        Supplier<T> operation
    ) {
        SecurityContext previous = SecurityContextHolder.getContext();
        try {
            SecurityContextHolder.setContext(context);
            return operation.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}

/**
 * Message wrapper that carries security context
 */
public class MessageWithContext<T> {
    private final T message;
    private final SecurityContext securityContext;
    
    public MessageWithContext(T message, SecurityContext securityContext) {
        this.message = message;
        this.securityContext = securityContext;
    }
    
    public T getMessage() {
        return message;
    }
    
    public SecurityContext getSecurityContext() {
        return securityContext;
    }
}
```

### 2. @Secured Annotation Support

Enable @Secured annotation on actor message handlers:

```java
/**
 * Annotation for securing actor message handlers
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface ActorSecured {
    /**
     * List of security authorities required
     */
    String[] value() default {};
    
    /**
     * Whether all authorities are required (AND) or any (OR)
     */
    boolean requireAll() default false;
}

/**
 * Example usage on actor
 */
@Component
@ActorSecured("ROLE_ADMIN")
public class AdminActor implements SpringActor<AdminCommand> {
    
    @Override
    public SpringActorBehavior<AdminCommand> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(AdminCommand.class, ctx)
            .onMessage(CreateUser.class, this::handleCreateUser)
            .onMessage(DeleteUser.class, this::handleDeleteUser)
            .build();
    }
    
    // Method-level security
    @ActorSecured("ROLE_SUPER_ADMIN")
    private void handleDeleteUser(DeleteUser cmd) {
        // Only SUPER_ADMIN can delete users
    }
}
```

### 3. Security Interceptor

Implement security checking interceptor:

```java
@Slf4j
public class ActorSecurityInterceptor {
    
    private final AuthorizationManager authorizationManager;
    
    /**
     * Intercept and check authorization before message processing
     */
    public <T> T intercept(
        Object actor,
        Method method,
        Object message,
        Supplier<T> proceed
    ) {
        // Extract security context from message
        SecurityContext context = extractSecurityContext(message);
        
        if (context == null) {
            log.warn("No security context found for message: {}", 
                message.getClass().getSimpleName());
            throw new AuthenticationCredentialsNotFoundException(
                "No security context available"
            );
        }
        
        // Check actor-level security
        ActorSecured actorAnnotation = AnnotationUtils.findAnnotation(
            actor.getClass(),
            ActorSecured.class
        );
        
        if (actorAnnotation != null) {
            checkAuthorization(context, actorAnnotation);
        }
        
        // Check method-level security
        ActorSecured methodAnnotation = AnnotationUtils.findAnnotation(
            method,
            ActorSecured.class
        );
        
        if (methodAnnotation != null) {
            checkAuthorization(context, methodAnnotation);
        }
        
        // Execute with security context
        return SecurityContextPropagator.executeWithContext(
            context,
            proceed
        );
    }
    
    private void checkAuthorization(
        SecurityContext context,
        ActorSecured annotation
    ) {
        Authentication auth = context.getAuthentication();
        
        if (auth == null || !auth.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException(
                "Authentication required"
            );
        }
        
        String[] requiredAuthorities = annotation.value();
        if (requiredAuthorities.length == 0) {
            return; // No specific authorities required
        }
        
        Collection<? extends GrantedAuthority> userAuthorities = 
            auth.getAuthorities();
        
        if (annotation.requireAll()) {
            // Require ALL authorities (AND)
            if (!hasAllAuthorities(userAuthorities, requiredAuthorities)) {
                throw new AccessDeniedException(
                    "Insufficient permissions. Required: " +
                    String.join(", ", requiredAuthorities)
                );
            }
        } else {
            // Require ANY authority (OR)
            if (!hasAnyAuthority(userAuthorities, requiredAuthorities)) {
                throw new AccessDeniedException(
                    "Insufficient permissions. Required one of: " +
                    String.join(", ", requiredAuthorities)
                );
            }
        }
    }
    
    private SecurityContext extractSecurityContext(Object message) {
        if (message instanceof MessageWithContext) {
            return ((MessageWithContext<?>) message).getSecurityContext();
        }
        return null;
    }
}
```

### 4. Spring Security Configuration

Create auto-configuration for security:

```java
@Configuration
@ConditionalOnClass(SecurityContextHolder.class)
@EnableConfigurationProperties(ActorSecurityProperties.class)
public class ActorSecurityAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public ActorSecurityInterceptor actorSecurityInterceptor(
        AuthorizationManager authorizationManager
    ) {
        return new ActorSecurityInterceptor(authorizationManager);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public AuthorizationManager authorizationManager(
        ActorSecurityProperties properties
    ) {
        return new DefaultAuthorizationManager(properties);
    }
    
    @Bean
    public SecurityContextPropagatingActorBehaviorFactory behaviorFactory(
        ActorSecurityInterceptor securityInterceptor
    ) {
        return new SecurityContextPropagatingActorBehaviorFactory(
            securityInterceptor
        );
    }
}

@ConfigurationProperties(prefix = "spring.actor.security")
public class ActorSecurityProperties {
    
    /**
     * Whether security is enabled for actors
     */
    private boolean enabled = true;
    
    /**
     * Default security mode
     */
    private SecurityMode defaultMode = SecurityMode.PERMISSIVE;
    
    /**
     * Whether to propagate security context automatically
     */
    private boolean propagateContext = true;
    
    /**
     * Whether to audit security decisions
     */
    private boolean auditDecisions = true;
    
    // Getters and setters
    
    public enum SecurityMode {
        /**
         * All actors require authentication
         */
        STRICT,
        
        /**
         * Only actors with @ActorSecured require authentication
         */
        PERMISSIVE,
        
        /**
         * Security is disabled
         */
        DISABLED
    }
}
```

### 5. ActorRef with Security Context

Extend ActorRef to support security context:

```java
/**
 * Extension to SpringActorRef with security context support
 */
public class SecureSpringActorRef<T> extends SpringActorRef<T> {
    
    private final SecurityContext securityContext;
    
    public SecureSpringActorRef(
        ActorRef<T> underlying,
        SecurityContext securityContext
    ) {
        super(underlying);
        this.securityContext = securityContext;
    }
    
    @Override
    public void tell(T message) {
        // Wrap message with security context
        MessageWithContext<T> wrapped = new MessageWithContext<>(
            message,
            securityContext
        );
        super.tell((T) wrapped);
    }
    
    /**
     * Create a secure actor ref from current security context
     */
    public static <T> SecureSpringActorRef<T> withCurrentContext(
        SpringActorRef<T> actorRef
    ) {
        SecurityContext context = SecurityContextHolder.getContext();
        return new SecureSpringActorRef<>(actorRef, context);
    }
}

/**
 * Extension to SpringActorSystem with security support
 */
public class SecureSpringActorSystem extends SpringActorSystem {
    
    /**
     * Spawn actor with security context
     */
    public <T> CompletableFuture<SecureSpringActorRef<T>> spawnSecure(
        Class<? extends SpringActor<T>> actorClass,
        String actorId
    ) {
        return spawn(actorClass, actorId)
            .thenApply(SecureSpringActorRef::withCurrentContext);
    }
    
    /**
     * Get or spawn actor with security context
     */
    public <T> CompletableFuture<SecureSpringActorRef<T>> getOrSpawnSecure(
        Class<? extends SpringActor<T>> actorClass,
        String actorId
    ) {
        return getOrSpawn(actorClass, actorId)
            .thenApply(SecureSpringActorRef::withCurrentContext);
    }
}
```

### 6. Integration with Spring Security Filters

Support standard Spring Security authentication mechanisms:

```java
/**
 * Extract authentication from HTTP request and pass to actor
 */
@RestController
@RequestMapping("/api/actors")
public class SecureActorController {
    
    @Autowired
    private SecureSpringActorSystem actorSystem;
    
    @PostMapping("/{actorId}/messages")
    @PreAuthorize("isAuthenticated()")
    public CompletableFuture<Response> sendMessage(
        @PathVariable String actorId,
        @RequestBody Command command
    ) {
        // Security context is automatically available via Spring Security
        return actorSystem
            .getOrSpawnSecure(CommandActor.class, actorId)
            .thenCompose(actor -> {
                CompletableFuture<Response> response = new CompletableFuture<>();
                actor.tell(new CommandWithReply(command, response));
                return response;
            });
    }
}
```

### 7. Custom Authentication Support

Support for custom authentication mechanisms:

```java
/**
 * Custom authentication provider for actor systems
 */
public interface ActorAuthenticationProvider {
    
    /**
     * Authenticate based on actor message
     */
    Authentication authenticate(Object message) throws AuthenticationException;
    
    /**
     * Check if this provider supports the message type
     */
    boolean supports(Class<?> messageClass);
}

/**
 * Example: API key authentication
 */
public class ApiKeyAuthenticationProvider implements ActorAuthenticationProvider {
    
    private final ApiKeyRepository apiKeyRepository;
    
    @Override
    public Authentication authenticate(Object message) 
        throws AuthenticationException {
        
        if (!(message instanceof ApiKeyMessage)) {
            throw new AuthenticationServiceException(
                "Unsupported message type"
            );
        }
        
        ApiKeyMessage apiKeyMsg = (ApiKeyMessage) message;
        String apiKey = apiKeyMsg.getApiKey();
        
        ApiKeyDetails details = apiKeyRepository.findByKey(apiKey)
            .orElseThrow(() -> new BadCredentialsException("Invalid API key"));
        
        if (!details.isActive()) {
            throw new DisabledException("API key is disabled");
        }
        
        return new ApiKeyAuthenticationToken(
            apiKey,
            details.getAuthorities()
        );
    }
    
    @Override
    public boolean supports(Class<?> messageClass) {
        return ApiKeyMessage.class.isAssignableFrom(messageClass);
    }
}
```

---

## Configuration Examples

```yaml
spring:
  actor:
    security:
      enabled: true
      default-mode: PERMISSIVE  # STRICT, PERMISSIVE, DISABLED
      propagate-context: true
      audit-decisions: true
      
      # Custom authentication providers
      providers:
        - type: api-key
          enabled: true
        - type: jwt
          enabled: true
          
  security:
    # Standard Spring Security configuration
    user:
      name: admin
      password: admin
      roles: ADMIN
```

---

## Deliverables

1. ✅ `SecurityContextPropagator` utility
2. ✅ `@ActorSecured` annotation
3. ✅ `ActorSecurityInterceptor` implementation
4. ✅ `ActorSecurityAutoConfiguration`
5. ✅ `SecureSpringActorRef` extension
6. ✅ `SecureSpringActorSystem` extension
7. ✅ `ActorAuthenticationProvider` interface
8. ✅ Configuration properties
9. ✅ Integration tests
10. ✅ Documentation and examples

---

## Success Criteria

- [ ] Security context propagates across actor calls
- [ ] @ActorSecured annotation works on actors and methods
- [ ] Spring Security authentication mechanisms work
- [ ] Custom authentication providers can be added
- [ ] AccessDeniedException thrown for unauthorized access
- [ ] Security works with async actor operations
- [ ] All security tests pass

---

## Testing Strategy

### Unit Tests
- Test security context propagation
- Test @ActorSecured annotation processing
- Test authorization checking logic
- Test custom authentication providers

### Integration Tests
- Test end-to-end authentication flow
- Test @PreAuthorize with actors
- Test multiple authentication mechanisms
- Test security across cluster nodes

---

## Notes

- Security context should be serializable for remote actors
- Consider performance impact of security checks
- Provide clear error messages for authorization failures
- Support both synchronous and asynchronous security checks
- Document security best practices
