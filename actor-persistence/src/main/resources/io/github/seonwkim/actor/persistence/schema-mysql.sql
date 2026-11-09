-- The journal, tag and snapshot tables of Pekko Persistence JDBC 1.1 (schema/<db>/<db>-create-schema.sql in
-- pekko-persistence-jdbc), without durable_state, which actor-persistence does not use. Safe to run again.

-- ordering is SERIAL, which is already unique: no separate index (MySQL has no CREATE INDEX IF NOT EXISTS).
CREATE TABLE IF NOT EXISTS event_journal(
    ordering SERIAL,
    deleted BOOLEAN DEFAULT false NOT NULL,
    persistence_id VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    writer TEXT NOT NULL,
    write_timestamp BIGINT NOT NULL,
    adapter_manifest TEXT NOT NULL,
    event_payload BLOB NOT NULL,
    event_ser_id INTEGER NOT NULL,
    event_ser_manifest TEXT NOT NULL,
    meta_payload BLOB,
    meta_ser_id INTEGER,meta_ser_manifest TEXT,
    PRIMARY KEY(persistence_id,sequence_number)
);


CREATE TABLE IF NOT EXISTS event_tag (
    event_id BIGINT UNSIGNED NOT NULL,
    tag VARCHAR(255) NOT NULL,
    PRIMARY KEY(event_id, tag),
    FOREIGN KEY (event_id)
        REFERENCES event_journal(ordering)
        ON DELETE CASCADE
    );

CREATE TABLE IF NOT EXISTS snapshot (
    persistence_id VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    created BIGINT NOT NULL,
    snapshot_ser_id INTEGER NOT NULL,
    snapshot_ser_manifest TEXT NOT NULL,
    snapshot_payload BLOB NOT NULL,
    meta_ser_id INTEGER,
    meta_ser_manifest TEXT,
    meta_payload BLOB,
  PRIMARY KEY (persistence_id, sequence_number));
