-- Orders table
CREATE TABLE IF NOT EXISTS orders (
    order_id VARCHAR(255) PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_orders_customer_id ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);

-- Order items table
CREATE TABLE IF NOT EXISTS order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id);

-- Order events table for event sourcing
CREATE TABLE IF NOT EXISTS order_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_data TEXT NOT NULL,
    sequence_number BIGINT NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    user_id VARCHAR(255),
    version BIGINT DEFAULT 0,
    CONSTRAINT unique_order_sequence UNIQUE (order_id, sequence_number)
);

CREATE INDEX IF NOT EXISTS idx_order_events_order_id ON order_events(order_id);
CREATE INDEX IF NOT EXISTS idx_order_events_timestamp ON order_events(timestamp);

-- Actor snapshots table
CREATE TABLE IF NOT EXISTS actor_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_id VARCHAR(255) NOT NULL,
    actor_type VARCHAR(100) NOT NULL,
    state_data TEXT NOT NULL,
    sequence_number BIGINT,
    version INTEGER DEFAULT 1,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_snapshots_actor ON actor_snapshots(actor_id, actor_type);
CREATE INDEX IF NOT EXISTS idx_snapshots_created_at ON actor_snapshots(created_at);
