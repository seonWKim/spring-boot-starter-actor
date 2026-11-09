# Monitoring

A Grafana dashboard and Prometheus alert rules for the meters of `actor-metrics`, under the names Micrometer's
Prometheus registry gives them.

- `grafana/actor-dashboard.json`: the **Actors** dashboard. Import it and pick your Prometheus data source.
- `prometheus/actor-alerts.yaml`: alert rules as a `PrometheusRule` (Prometheus Operator). Without the
  operator, `yq '.spec' prometheus/actor-alerts.yaml` is a plain rules file.
- `grafana/provisioning/`: Grafana provisioning that loads the dashboard and a Prometheus data source at startup.
- `docker-compose.yml`: Prometheus and Grafana on your machine with all of the above loaded.

The meters, the panels and every alert are described in the
[Monitoring](https://seonwkim.github.io/actor-spring-boot-starter/guides/operations/monitoring/) guide.
`MonitoringFilesTest` (actor-autoconfigure) fails when a query names a meter the docs don't describe, and
`GrafanaProvisioningTest` fails when the provisioning no longer matches the dashboard or the compose stack.

## Set up Grafana

Quick start, with your application on port 8080 exposing `/actuator/prometheus`:

```bash
cd monitoring
docker compose up -d              # Grafana on http://localhost:3000 (admin/admin), Prometheus on :9090
```

Files for your version (each GitHub release attaches them), provisioning your own Grafana, and Kubernetes:
[Set up Grafana](https://seonwkim.github.io/actor-spring-boot-starter/guides/operations/monitoring/#set-up-grafana).
