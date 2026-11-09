CREATE TABLE IF NOT EXISTS actor_peer (
    service_name VARCHAR(200) NOT NULL,
    node_id VARCHAR(500) NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INT NOT NULL,
    last_seen TIMESTAMP NOT NULL,
    PRIMARY KEY (service_name, node_id)
);
