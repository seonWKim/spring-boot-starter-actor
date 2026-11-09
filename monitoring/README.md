# Monitoring

A Grafana dashboard and Prometheus alert rules for the meters of `actor-metrics`, under the names Micrometer's
Prometheus registry gives them.

- `grafana/actor-dashboard.json`: the **Actors** dashboard. Import it and pick your Prometheus data source.
- `prometheus/actor-alerts.yaml`: alert rules as a `PrometheusRule` (Prometheus Operator). Without the
  operator, `yq '.spec' prometheus/actor-alerts.yaml` is a plain rules file.

The meters, the panels and every alert are described in the
[Monitoring](https://seonwkim.github.io/actor-spring-boot-starter/examples/monitoring/) docs.
`MonitoringFilesTest` (actor-autoconfigure) fails when a query names a meter the docs don't describe. The
Kubernetes example installs both: `example/kubernetes/scripts/monitoring.sh`.
