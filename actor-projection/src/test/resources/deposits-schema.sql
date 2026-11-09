CREATE TABLE IF NOT EXISTS deposits (
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  amount BIGINT NOT NULL,
  PRIMARY KEY (persistence_id, sequence_number)
);
