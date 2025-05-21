# Spring Boot Pekko Chat Example with Monitoring

This example demonstrates a Spring Boot application using Pekko actors with Prometheus and Grafana monitoring.

## Prerequisites

- Docker and Docker Compose installed on your machine
- Java 17 or higher

## Running the Application

1. Start the application instances (you can run one or more instances):
   ```bash
   ./gradlew :example:chat:bootRun
   ```

2. Start Prometheus and Grafana:
```bash
cd example/chat
docker-compose up -d
```

3. Access the monitoring dashboards:
   - Prometheus: http://localhost:9090
   - Grafana: http://localhost:3000 (login with admin/admin)

## Stopping the Containers

To stop the monitoring containers:
```bash
cd example/chat
docker-compose down
```

## Grafana Dashboard

A pre-configured Pekko Actors dashboard is automatically provisioned in Grafana. To access it:

1. Open http://localhost:3000 in your browser
2. Log in with username `admin` and password `admin`
3. Navigate to Dashboards -> Pekko -> Pekko Actors Dashboard

The dashboard includes the following metrics:

- **Processed Messages**: Total number of messages processed by each actor
- **Processing Time**: Average time spent processing messages (in milliseconds)
- **Mailbox Size**: Current size of each actor's mailbox
- **Actor Errors**: Total number of errors encountered by each actor

## Creating Custom Dashboards

You can create custom dashboards in Grafana by:

1. Click on the "+" icon in the left sidebar
2. Select "Dashboard"
3. Add panels using the Prometheus data source
4. Use metrics with the `pekko_` prefix for Pekko-specific metrics

Common Pekko metrics include:
- `pekko_actor_processed_messages_total`
- `pekko_actor_processing_time_ns`
- `pekko_actor_mailbox_size`
- `pekko_actor_errors_total`

## Troubleshooting

If you don't see any metrics in Grafana:

1. Check that your application is running and exposing metrics at `/actuator/prometheus`
2. Verify that Prometheus can reach your application by checking the Targets page in Prometheus UI
3. Ensure that the Prometheus data source is correctly configured in Grafana
