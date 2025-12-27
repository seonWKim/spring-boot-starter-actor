# Production Deployment Checklist

Use this checklist when deploying spring-boot-starter-actor to production environments.

## Pre-Deployment

### Configuration Review

- [ ] **Actor System Configuration**
  - [ ] Provider set correctly (local vs cluster)
  - [ ] Actor system name configured
  - [ ] Dispatcher configuration reviewed and optimized
  - [ ] Mailbox configuration appropriate for load

- [ ] **Cluster Configuration** (if using cluster mode)
  - [ ] Seed nodes properly configured
  - [ ] Network addresses/hostnames correct
  - [ ] Ports accessible (default: 2551)
  - [ ] Split-brain resolver configured
  - [ ] TLS/SSL enabled for cluster communication

- [ ] **Security**
  - [ ] TLS/SSL certificates valid and deployed
  - [ ] Secrets stored securely (not in source code)
  - [ ] Environment variables configured
  - [ ] Network security groups/firewall rules set
  - [ ] Authentication/authorization implemented

- [ ] **Monitoring & Observability**
  - [ ] Metrics module enabled
  - [ ] Spring Boot Actuator configured
  - [ ] Health checks exposed
  - [ ] Logging configured (level, format, destination)
  - [ ] MDC context propagation enabled
  - [ ] Distributed tracing configured (if applicable)

- [ ] **Persistence**
  - [ ] Database connections configured
  - [ ] Connection pool settings optimized
  - [ ] Backup strategy in place
  - [ ] Data migration scripts tested

- [ ] **Resource Limits**
  - [ ] JVM heap size configured
  - [ ] Thread pool sizes set appropriately
  - [ ] Mailbox sizes configured
  - [ ] Timeout values appropriate for load

### Testing

- [ ] **Functional Testing**
  - [ ] All features tested in staging
  - [ ] Integration tests passed
  - [ ] End-to-end tests passed

- [ ] **Performance Testing**
  - [ ] Load testing completed
  - [ ] Performance benchmarks meet requirements
  - [ ] Memory usage acceptable under load
  - [ ] No memory leaks detected

- [ ] **Cluster Testing** (if applicable)
  - [ ] Cluster formation tested
  - [ ] Node join/leave tested
  - [ ] Split-brain scenarios tested
  - [ ] Failover tested
  - [ ] Rolling update tested

- [ ] **Resilience Testing**
  - [ ] Failure scenarios tested
  - [ ] Recovery procedures verified
  - [ ] Supervision strategies validated
  - [ ] Circuit breaker tested (if implemented)

### Documentation

- [ ] **Deployment Documentation**
  - [ ] Architecture diagram updated
  - [ ] Configuration documented
  - [ ] Dependencies documented
  - [ ] Environment requirements documented

- [ ] **Operations Documentation**
  - [ ] Runbook created
  - [ ] Monitoring guide available
  - [ ] Troubleshooting guide reviewed
  - [ ] Recovery procedures documented

- [ ] **Team Readiness**
  - [ ] Team trained on actor model concepts
  - [ ] On-call procedures defined
  - [ ] Escalation paths documented
  - [ ] Access permissions configured

## Deployment

### Before Starting

- [ ] **Communication**
  - [ ] Stakeholders notified
  - [ ] Maintenance window scheduled (if needed)
  - [ ] Rollback plan prepared

- [ ] **Backup**
  - [ ] Current state backed up
  - [ ] Database backup completed
  - [ ] Configuration backup saved

### Deployment Process

- [ ] **Container/Application Deployment**
  - [ ] Images built and tagged
  - [ ] Images scanned for vulnerabilities
  - [ ] Images pushed to registry
  - [ ] Deployment manifests reviewed

- [ ] **Rolling Update** (if cluster)
  - [ ] Deploy one node at a time
  - [ ] Verify node joins cluster
  - [ ] Check actor rebalancing
  - [ ] Monitor for errors before proceeding

- [ ] **Configuration**
  - [ ] Environment variables set
  - [ ] ConfigMaps/Secrets applied (Kubernetes)
  - [ ] Feature flags configured

### Health Checks

- [ ] **Application Health**
  - [ ] Application starts successfully
  - [ ] Health endpoint responding
  - [ ] Readiness probe passing
  - [ ] Liveness probe passing

- [ ] **Cluster Health** (if applicable)
  - [ ] All nodes joined cluster
  - [ ] Cluster leader elected
  - [ ] Sharding initialized
  - [ ] Actors distributed correctly

- [ ] **Connectivity**
  - [ ] External services reachable
  - [ ] Database connections working
  - [ ] Message queues accessible (if used)
  - [ ] APIs responding

### Monitoring Setup

- [ ] **Metrics Collection**
  - [ ] Prometheus/metrics endpoint accessible
  - [ ] Key metrics being collected
  - [ ] Dashboards displaying data
  - [ ] Baseline metrics recorded

- [ ] **Alerting**
  - [ ] Alert rules configured
  - [ ] Notification channels tested
  - [ ] On-call rotation updated
  - [ ] Escalation policies active

- [ ] **Logging**
  - [ ] Logs flowing to aggregator
  - [ ] Log queries working
  - [ ] Retention policies configured
  - [ ] Sensitive data masked

## Post-Deployment

### Immediate Verification (0-1 hours)

- [ ] **Basic Functionality**
  - [ ] Critical user flows working
  - [ ] Actors spawning correctly
  - [ ] Messages being processed
  - [ ] No error spikes in logs

- [ ] **Performance**
  - [ ] Response times acceptable
  - [ ] CPU/memory usage normal
  - [ ] Thread pool utilization healthy
  - [ ] Mailbox sizes reasonable

- [ ] **Metrics**
  - [ ] `system.active-actors` tracking
  - [ ] `actor.processing-time` reasonable
  - [ ] `dispatcher.threads.active` normal
  - [ ] No dead letters spike

### Short-term Monitoring (1-24 hours)

- [ ] **Stability**
  - [ ] No crashes or restarts
  - [ ] Memory usage stable
  - [ ] No resource leaks
  - [ ] Error rates normal

- [ ] **Cluster Stability** (if applicable)
  - [ ] No unexpected node departures
  - [ ] Shard distribution stable
  - [ ] No split-brain events
  - [ ] Leader election stable

- [ ] **Business Metrics**
  - [ ] Transaction volumes normal
  - [ ] User satisfaction metrics OK
  - [ ] SLA compliance maintained

### Long-term Monitoring (1-7 days)

- [ ] **Trends**
  - [ ] Resource usage trends reviewed
  - [ ] Performance trends acceptable
  - [ ] Error trends analyzed
  - [ ] Capacity planning updated

- [ ] **Optimization**
  - [ ] Identify optimization opportunities
  - [ ] Tune configuration if needed
  - [ ] Address any performance issues

## Rollback Procedure

If issues are detected:

1. [ ] **Assess Severity**
   - Determine if rollback is necessary
   - Consider partial rollback (specific nodes)

2. [ ] **Execute Rollback**
   - Deploy previous version
   - Verify cluster stability
   - Check data consistency

3. [ ] **Restore State** (if needed)
   - Restore database from backup
   - Verify data integrity

4. [ ] **Post-Rollback**
   - Notify stakeholders
   - Analyze root cause
   - Plan remediation

## Key Metrics to Monitor

### System Metrics
- `system.active-actors` - Number of active actors
- `system.processed-messages` - Message throughput
- `system.dead-letters` - Undelivered messages
- `system.unhandled-messages` - Unhandled message count

### Actor Metrics
- `actor.processing-time` - Message processing latency
- `actor.time-in-mailbox` - Queueing time
- `actor.mailbox-size` - Queue depth
- `actor.errors` - Processing errors

### Dispatcher Metrics
- `dispatcher.threads.active` - Thread utilization
- `dispatcher.queue.size` - Task queue depth
- `dispatcher.tasks.rejected` - Rejected tasks

### Cluster Metrics (if applicable)
- `sharding.region.hosted-shards` - Shard distribution
- `sharding.region.hosted-entities` - Entity distribution
- `remote.messages.inbound.count` - Remote messages

### Application Metrics
- JVM heap usage
- GC pause time
- HTTP response times
- Database query times

## Alert Thresholds (Example)

Configure alerts for:

- **Critical**
  - Actor system down
  - Cluster split-brain detected
  - Error rate > 10% of requests
  - Memory usage > 90%

- **Warning**
  - Actor processing time > 5s (p95)
  - Mailbox size > 1000 messages
  - Dead letter rate increasing
  - CPU usage > 80%

- **Info**
  - Deployment completed
  - Node joined/left cluster
  - Configuration changed

## Environment-Specific Notes

### Development
- Local mode usually sufficient
- Debug logging enabled
- Metrics optional

### Staging
- Mirror production configuration
- Cluster mode if production uses it
- Full monitoring enabled
- Used for load testing

### Production
- Cluster mode for HA
- TLS/SSL mandatory
- Full monitoring and alerting
- Secrets management required
- Regular backups
- Disaster recovery plan

## Common Issues & Solutions

| Issue | Check | Solution |
|-------|-------|----------|
| Cluster won't form | Seed nodes, network | Verify network connectivity, check seed node config |
| High memory usage | Actor count, mailbox sizes | Review actor lifecycle, implement passivation |
| Slow processing | Thread pool, blocking operations | Tune dispatcher, remove blocking calls |
| Split brain | Network partition, SBR config | Configure split-brain resolver, test scenarios |
| Messages lost | Dead letters | Check actor lifecycle, supervision strategies |

## Support Contacts

- **Primary Contact**: [Name/Email]
- **Secondary Contact**: [Name/Email]
- **Escalation**: [Name/Email]
- **Vendor Support**: [If applicable]

## Approval

- [ ] Development Lead approval
- [ ] Operations Lead approval
- [ ] Security review completed
- [ ] Business stakeholder notified

---

**Deployment Date**: _______________
**Deployed By**: _______________
**Version**: _______________
**Environment**: _______________

---

For issues during deployment, refer to:
- [Troubleshooting Guide](troubleshooting.md)
- [Best Practices](best-practices.md)
- [FAQ](faq.md)
