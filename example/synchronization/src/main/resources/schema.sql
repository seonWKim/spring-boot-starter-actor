-- Counter table schema for MySQL
CREATE TABLE IF NOT EXISTS counter (
    counter_id VARCHAR(255) NOT NULL,
    value      BIGINT       NOT NULL,
    version    BIGINT       NOT NULL,
    PRIMARY KEY (counter_id)
)
    ENGINE = InnoDB;

-- Note: The primary key already creates an index on counter_id
-- This script can be run directly in MySQL to create the counter table
