package io.github.seonwkim.example.persistence.snapshot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ActorSnapshotRepository extends JpaRepository<ActorSnapshot, Long> {

    Optional<ActorSnapshot> findTopByActorIdAndActorTypeOrderByCreatedAtDesc(
        String actorId, String actorType);

    List<ActorSnapshot> findByActorIdAndActorTypeOrderByCreatedAtDesc(
        String actorId, String actorType);

    @Modifying
    @Query("DELETE FROM ActorSnapshot s WHERE s.actorId = :actorId " +
           "AND s.actorType = :actorType AND s.createdAt < :cutoff")
    int deleteOldSnapshots(
        @Param("actorId") String actorId,
        @Param("actorType") String actorType,
        @Param("cutoff") Instant cutoff);

    @Query("SELECT COUNT(s) FROM ActorSnapshot s WHERE s.actorId = :actorId " +
           "AND s.actorType = :actorType")
    long countSnapshots(@Param("actorId") String actorId, @Param("actorType") String actorType);
}
