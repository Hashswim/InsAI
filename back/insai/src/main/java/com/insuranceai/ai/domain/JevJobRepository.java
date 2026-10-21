package com.insuranceai.ai.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface JevJobRepository extends JpaRepository<JevJob, UUID> {
    interface StatusCount { JevJob.Status getStatus(); long getTotal(); }
    @Query("select j.status as status, count(j) as total from JevJob j where j.caseId=:caseId and j.createdAt>=:since and j.createdAt<:until group by j.status")
    List<StatusCount> statusCounts(@Param("caseId") UUID caseId, @Param("since") Instant since, @Param("until") Instant until);
    Optional<JevJob> findByRequestedByAndIdempotencyKey(UUID user, UUID key);
    List<JevJob> findTop10ByStatusOrderByCreatedAtAsc(JevJob.Status status);

    @Modifying(clearAutomatically=true, flushAutomatically=true)
    @Query("update JevJob j set j.status = 'RUNNING', j.startedAt = :now, j.updatedAt = :now where j.id = :id and j.status = 'QUEUED'")
    int claim(@Param("id") UUID id, @Param("now") Instant now);

    @Query("select j.id from JevJob j where j.status = 'RUNNING' and j.startedAt < :cutoff order by j.startedAt, j.id")
    List<UUID> findExpiredIds(@Param("cutoff") Instant cutoff, org.springframework.data.domain.Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from JevJob j where j.id = :id")
    Optional<JevJob> lockById(@Param("id") UUID id);
}
