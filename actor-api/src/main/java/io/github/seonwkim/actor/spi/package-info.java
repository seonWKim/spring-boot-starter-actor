/**
 * Extension points: small plain-Java interfaces a backend implements so the starter can keep its
 * state in a store the application already runs. Built-in implementations live in
 * {@code actor-jdbc} and {@code actor-redis}; an application bean of the same type replaces them.
 * Nothing here depends on Pekko.
 */
package io.github.seonwkim.actor.spi;
