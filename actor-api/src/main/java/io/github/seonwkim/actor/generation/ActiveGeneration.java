package io.github.seonwkim.actor.generation;

import java.time.Instant;

/**
 * The deployment that receives traffic: which color, at which epoch.
 *
 * @param color the active color, for example {@code blue} or {@code green}
 * @param epoch grows by one with every activation and never goes back, so a color that is activated
 *     again (blue, then green, then blue) gets a new epoch and work from its earlier turn stays stale
 * @param operationId the deployment operation that activated it
 * @param activatedAt when it was activated, on the store's clock
 */
public record ActiveGeneration(String color, long epoch, String operationId, Instant activatedAt) {}
