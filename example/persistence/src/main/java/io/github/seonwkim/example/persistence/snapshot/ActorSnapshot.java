package io.github.seonwkim.example.persistence.snapshot;

import java.time.Instant;
import javax.persistence.*;

@Entity
@Table(name = "actor_snapshots")
public class ActorSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Column(name = "actor_type", nullable = false)
    private String actorType;

    @Column(name = "state_data", columnDefinition = "TEXT")
    private String stateData;

    @Column(name = "sequence_number")
    private Long sequenceNumber;

    @Column(name = "version")
    private Integer version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ActorSnapshot() {}

    public ActorSnapshot(String actorId, String actorType, String stateData) {
        this.actorId = actorId;
        this.actorType = actorType;
        this.stateData = stateData;
        this.createdAt = Instant.now();
        this.version = 1;
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getActorType() {
        return actorType;
    }

    public void setActorType(String actorType) {
        this.actorType = actorType;
    }

    public String getStateData() {
        return stateData;
    }

    public void setStateData(String stateData) {
        this.stateData = stateData;
    }

    public Long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(Long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
