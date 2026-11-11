/**
 * Blue-green activation and fencing: which deployment (color) is active, at which epoch, changed by a
 * compare-and-set in a shared store, with an audit event for every attempt. See
 * {@link io.github.seonwkim.actor.generation.DeploymentGeneration}.
 *
 * <p>Parameters, return values and fields are non-null unless annotated {@code @Nullable}.
 */
@NonNullApi
@NonNullFields
package io.github.seonwkim.actor.generation;

import org.springframework.lang.NonNullApi;
import org.springframework.lang.NonNullFields;
