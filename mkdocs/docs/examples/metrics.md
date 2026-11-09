# Metrics Example

The simple example's `HelloActor` with actor metrics turned on and exposed to Prometheus through Spring
Boot Actuator. Use it to see which meters an app gets and how restarts and stops show up in them.

- **Source:** [`example/metrics-example`](https://github.com/seonWKim/actor-spring-boot-starter/tree/main/example/metrics-example)
- **Runs:** one node, no cluster
- **Modules:** `actor-spring-boot-starter`, `actor-metrics` (with its optional Java agent attached),
  Spring WebFlux, Actuator, `micrometer-registry-prometheus`

The full list of meters, their tags, the Grafana dashboard and the alert rules are in the
[Monitoring guide](../guides/operations/monitoring.md). This page covers only the example.

## What it shows

- The dependencies and the Actuator setting that turn on actor metrics
- Attaching the `actor-metrics` Java agent from Gradle (`bootRun` and `test`)
- Message, failure, restart and mailbox meters moving as you call the actor

## Run it

```bash
./gradlew :example:metrics-example:bootRun
```

`bootRun` builds the agent jar (`:actor-metrics:agentJar`) first and starts the JVM with
`-javaagent:...agent.jar`. The app listens on port 8080.

Send some traffic, then read the meters:

```bash
for i in $(seq 1 20); do curl -s http://localhost:8080/hello > /dev/null; done
curl http://localhost:8080/hello/restart
curl http://localhost:8080/hello/stop

curl -s http://localhost:8080/actuator/prometheus | grep '^actor_'
```

Other endpoints:

| Endpoint | What it does |
|---|---|
| `GET /hello` | Asks `hello-actor` for a greeting (spawns it if needed) |
| `GET /hello/restart` | Makes the actor throw, so supervision restarts it |
| `GET /hello/stop` | Stops the actor |
| `GET /debug/actor-path` | Returns the actor's path, the value the opt-in `actor.path` tag uses |
| `GET /actuator/prometheus` | All meters in Prometheus format |
| `GET /actuator/metrics` | Meter names, for a quick look without Prometheus |

## Walkthrough

### Dependencies

```kotlin
dependencies {
    implementation(project(":actor-spring-boot-starter"))
    implementation(project(":actor-metrics"))

    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
}
```

In your own app, use the published coordinates (`io.github.seonwkim:actor-metrics`). With a
`MeterRegistry` bean on the classpath (Actuator creates one), the starter meters every actor it spawns.
No code is needed. `spring.actor.metrics.enabled=false` turns it off.

### Expose the endpoint

```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus, health, info, metrics
```

### Attach the agent (optional)

The starter records the main meters without any agent. The example also attaches the `actor-metrics`
Java agent, which adds a few meters that no public hook exposes (see
[Monitoring](../guides/operations/monitoring.md)). The Gradle wiring:

```kotlin
tasks.named<BootRun>("bootRun") {
    dependsOn(":actor-metrics:agentJar")
    val agentJar = project(":actor-metrics").tasks.named<Jar>("agentJar").get()
    jvmArgs = listOf("-javaagent:${agentJar.archiveFile.get().asFile.absolutePath}")
}
```

The `test` task gets the same `-javaagent` argument, so tests run with the agent too.

### The actor

`HelloActor`, `HelloService` and `HelloController` are the same as in the [simple example](simple.md),
plus the `/debug/actor-path` endpoint. The actor restarts on failure, up to 10 times a minute, so
`/hello/restart` increases the restart counter.

## Try next

- Call `/hello/restart` a few times and watch `actor_lifecycle_restarts_total` grow.
- Run without the agent: remove the `jvmArgs` line (or run the jar with plain `java -jar`) and compare
  the `actor_` meters.
- Start a Prometheus and Grafana and import the dashboard from the repository's
  [`monitoring/`](https://github.com/seonWKim/actor-spring-boot-starter/tree/main/monitoring) directory.
- For metrics in a cluster, see the [chat example](chat.md) and the [Kubernetes example](kubernetes.md).

## Related

- [Monitoring](../guides/operations/monitoring.md): every meter, the dashboard, alerts, and the agent
- [Production](../guides/operations/production.md): health checks and graceful shutdown
- [Configuration](../guides/operations/configuration.md)
