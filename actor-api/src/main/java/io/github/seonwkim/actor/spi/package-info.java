/**
 * Extension points: small plain-Java interfaces a backend implements so the starter can keep its
 * state in a store the application already runs. Built-in implementations live in
 * {@code actor-jdbc} and {@code actor-redis}; an application bean of the same type replaces them.
 * Nothing here depends on Pekko. Parameters, return values and fields are non-null unless
 * annotated {@code @Nullable}.
 */
@NonNullApi
@NonNullFields
package io.github.seonwkim.actor.spi;

import org.springframework.lang.NonNullApi;
import org.springframework.lang.NonNullFields;
