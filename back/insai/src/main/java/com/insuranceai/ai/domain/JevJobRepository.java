package com.insuranceai.ai.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface JevJobRepository extends JpaRepository<JevJob, UUID> {
    Optional<JevJob> findByRequestedByAndIdempotencyKey(UUID user, UUID key);
    List<JevJob> findTop10ByStatusOrderByCreatedAtAsc(JevJob.Status status);

    @Modifying(clearAutomatically=true, flushAutomatically=true)
    @Query("update JevJob j set j.status = 'RUNNING', j.startedAt = :now, j.updatedAt = :now where j.id = :id and j.status = 'QUEUED'")
    int claim(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying(clearAutomatically=true, flushAutomatically=true)
    @Query("update JevJob j set j.status = 'UNKNOWN', j.errorCode = 'EXECUTION_UNCERTAIN', j.finishedAt = :now, j.updatedAt = :now where j.status = 'RUNNING' and j.startedAt < :cutoff")
    int expire(@Param("cutoff") Instant cutoff, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from JevJob j where j.id = :id")
    Optional<JevJob> lockById(@Param("id") UUID id);
}
