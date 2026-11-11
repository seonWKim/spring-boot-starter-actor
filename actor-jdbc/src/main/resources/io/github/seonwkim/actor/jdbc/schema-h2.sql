CREATE TABLE IF NOT EXISTS actor_peer (
    service_name VARCHAR(200) NOT NULL,
    node_id VARCHAR(500) NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INT NOT NULL,
    last_seen TIMESTAMP NOT NULL,
    PRIMARY KEY (service_name, node_id)
);

CREATE TABLE IF NOT EXISTS actor_lease (
    lease_name VARCHAR(500) NOT NULL,
    owner VARCHAR(500) NOT NULL,
    expires_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (lease_name)
);

CREATE TABLE IF NOT EXISTS actor_generation (
    deployment VARCHAR(200) NOT NULL,
    epoch BIGINT NOT NULL,
    color VARCHAR(200) NOT NULL,
    operation_id VARCHAR(200) NOT NULL,
    activated_at BIGINT NOT NULL,
    PRIMARY KEY (deployment, epoch),
    CONSTRAINT actor_generation_operation UNIQUE (deployment, operation_id)
);
