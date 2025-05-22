# Monitoring

This project includes built-in monitoring using Prometheus and Grafana, managed via Docker Compose.

## How to Set Up

### Start the Monitoring Stack

Run the following command to start Prometheus and Grafana:

```shell 
$ docker-compose up -d 
```

This will start:

Prometheus at http://localhost:9090

Grafana at http://localhost:3000

### Shutdown and Clean Up

To stop and remove the containers and associated volumes:

```shell
$ docker-compose down -v  
```

### Prometheus 

- Web UI: http://localhost:9090
- Scrape targets configured:
  - host.docker.internal:8080 
  - host.docker.internal:8081 
  - host.docker.internal:8082
- Configuration file: scripts/monitoring/grafana/prometheus.yml

To verify that Prometheus is scraping successfully, visit: http://localhost:9090/targets

### Grafana 

- Web UI: http://localhost:3000
- Default credentials:
  - Username: admin 
  - Password: admin 
