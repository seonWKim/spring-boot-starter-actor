# Monitoring

A Grafana dashboard and Prometheus alert rules for the meters of `actor-metrics`, under the names Micrometer's
Prometheus registry gives them.

- `grafana/actor-dashboard.json`: the **Actors** dashboard. Import it and pick your Prometheus data source.
- `prometheus/actor-alerts.yaml`: alert rules as a `PrometheusRule` (Prometheus Operator). Without the
  operator, `yq '.spec' prometheus/actor-alerts.yaml` is a plain rules file.
- `grafana/provisioning/`: Grafana provisioning that loads the dashboard and a Prometheus data source at startup.
- `docker-compose.yml`: Prometheus and Grafana on your machine with all of the above loaded.

The meters, the panels and every alert are described in the
[Monitoring](https://seonwkim.github.io/actor-spring-boot-starter/examples/monitoring/) docs.
`MonitoringFilesTest` (actor-autoconfigure) fails when a query names a meter the docs don't describe, and
`GrafanaProvisioningTest` fails when the provisioning no longer matches the dashboard or the compose stack.

## Set up Grafana

### Download the files for your version

Use the files of the release you depend on: the dashboard on `main` may query meters a later release added.
Each GitHub release attaches them, with the version in their names:

```text
https://github.com/seonWKim/actor-spring-boot-starter/releases/download/v<version>/actor-grafana-dashboard-<version>.json
https://github.com/seonWKim/actor-spring-boot-starter/releases/download/v<version>/actor-prometheus-alerts-<version>.yaml
https://github.com/seonWKim/actor-spring-boot-starter/releases/download/v<version>/actor-monitoring-<version>.zip
```

The dashboard carries the version as a `v<version>` tag and at the end of its description. The zip holds this
directory for that version: the dashboard, the alert rules, the provisioning files and `docker-compose.yml`.

### Provisioning

To load the dashboard into your own Grafana without clicks, copy the two provisioning files into Grafana's
provisioning directory (`/etc/grafana/provisioning` by default) and the dashboard into the directory the
dashboard provider reads:

| File | Goes to | Change |
|---|---|---|
| `grafana/provisioning/datasources/prometheus.yaml` | `provisioning/datasources/` | `url`: your Prometheus. Skip the file if Grafana already has a default Prometheus data source |
| `grafana/provisioning/dashboards/actor.yaml` | `provisioning/dashboards/` | `options.path` if you keep dashboards elsewhere |
| `grafana/actor-dashboard.json` | `/var/lib/grafana/dashboards/actor/` (the provider's `options.path`) | |

The dashboard's **Data source** variable starts on Grafana's default Prometheus data source.

### Run it locally

With your application running on port 8080 and exposing `/actuator/prometheus`:

```bash
cd monitoring                     # or the unzipped actor-monitoring-<version>/
docker compose up -d
```

- Grafana: <http://localhost:3000> (`admin` / `admin`), dashboard **Actors** in the **Actors** folder.
- Prometheus: <http://localhost:9090>, the alert rules under *Alerts*, the scrape target under *Status > Targets*.

Prometheus scrapes `host.docker.internal:8080/actuator/prometheus`. Set `ACTOR_TARGET` for another address:

```bash
ACTOR_TARGET=host.docker.internal:9000 docker compose up -d
docker compose down -v            # stop and remove the containers and the generated files
```

The `setup` service writes the scrape target and turns `actor-alerts.yaml` into a plain rules file
(its `spec`) before Prometheus starts. The cluster alerts group by `namespace`, which a local stack doesn't
have, so they treat everything Prometheus scrapes as one group.

### Kubernetes

With the Prometheus Operator (for example kube-prometheus-stack), the Kubernetes example installs the
ServiceMonitor, the alert rules and the dashboard (as a ConfigMap for Grafana's dashboard sidecar):
`example/kubernetes/scripts/monitoring.sh`.
