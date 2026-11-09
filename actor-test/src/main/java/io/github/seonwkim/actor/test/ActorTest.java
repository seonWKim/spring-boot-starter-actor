package io.github.seonwkim.actor.test;

import io.github.seonwkim.actor.EnableActorSupport;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.OverrideAutoConfiguration;
import org.springframework.boot.test.autoconfigure.filter.TypeExcludeFilters;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * A test that starts only the actor system and the actor beans, like {@code @DataJpaTest} does for
 * JPA. Web, data and the application's other auto-configuration stay off, and the component scan
 * keeps only {@code SpringActor} and {@code SpringShardedActor} beans (all of them, or the ones
 * named in {@link #value()}).
 *
 * <pre>{@code
 * @ActorTest(GreeterActor.class)
 * class GreeterActorTest {
 *     @Autowired SpringActorSystem actorSystem;
 * }
 * }</pre>
 *
 * <p>Other beans the actors need are added with {@code @Import} or {@code @MockitoBean}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@BootstrapWith(ActorTestContextBootstrapper.class)
@ExtendWith(SpringExtension.class)
@OverrideAutoConfiguration(enabled = false)
@TypeExcludeFilters(ActorTypeExcludeFilter.class)
@AutoConfigureActorTest
@EnableActorSupport
public @interface ActorTest {

    /** Alias for {@link #actors()}. */
    @AliasFor("actors")
    Class<?>[] value() default {};

    /** The actor beans to start; every actor bean found by the component scan when empty. */
    @AliasFor("value")
    Class<?>[] actors() default {};

    /** Properties in {@code key=value} form added to the environment before the test runs. */
    String[] properties() default {};

    /** Whether the component scan keeps actor beans; false keeps only {@link #includeFilters()}. */
    boolean useDefaultFilters() default true;

    /** More beans the component scan keeps. */
    Filter[] includeFilters() default {};

    /** Beans the component scan leaves out. */
    Filter[] excludeFilters() default {};

    /** Auto-configuration to leave out. */
    @AliasFor(annotation = ImportAutoConfiguration.class, attribute = "exclude")
    Class<?>[] excludeAutoConfiguration() default {};
}
