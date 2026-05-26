package com.enterprise.techdebt.repo;

import com.enterprise.techdebt.domain.Issue;
import com.enterprise.techdebt.domain.IssueSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface IssueRepository extends JpaRepository<Issue, Long>, JpaSpecificationExecutor<Issue> {
    Optional<Issue> findByProjectIdAndSourceAndExternalId(Long projectId, IssueSource source, String externalId);

    /**
     * Analytics: count issues grouped by (project_id, source, severity, status).
     * Returns Object[] rows: [Long projectId, String source, String severity, String status, Long count].
     * The DB does the GROUP BY rather than the app iterating over findAll().
     */
    @Query("select i.projectId, cast(i.source as string), cast(i.severity as string), cast(i.status as string), count(i) "
            + "from Issue i group by i.projectId, i.source, i.severity, i.status")
    List<Object[]> aggregateForRollup();

    @Query("select cast(i.source as string), count(i) from Issue i "
            + "where i.firstSeen >= :since group by i.source")
    List<Object[]> countIngestedBySource(@Param("since") Instant since);

    @Query("select cast(i.source as string), count(i) from Issue i "
            + "where i.status in ('OPEN', 'IN_PROGRESS') group by i.source")
    List<Object[]> countOpenBySource();

    /**
     * For MTTR: per-source rows of (source, firstSeen, updatedAt) for resolved issues.
     * Time math stays in the app (vendor-portable) but the WHERE clause keeps the dataset small.
     */
    @Query("select cast(i.source as string), i.firstSeen, i.updatedAt from Issue i "
            + "where i.status in ('FIXED', 'FIXED_EXTERNALLY') "
            + "and i.firstSeen is not null and i.updatedAt is not null")
    List<Object[]> resolvedIssueTimings();
}
