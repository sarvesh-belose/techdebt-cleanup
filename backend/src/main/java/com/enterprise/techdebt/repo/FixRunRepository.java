package com.enterprise.techdebt.repo;

import com.enterprise.techdebt.domain.FixRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface FixRunRepository extends JpaRepository<FixRun, Long> {
    Page<FixRun> findAllByProjectId(Long projectId, Pageable pageable);

    /** Per-state counts since the given instant, for the success-rate dashboard. */
    @Query("select cast(r.state as string), count(r) from FixRun r "
            + "where r.createdAt >= :since group by r.state")
    List<Object[]> countByStateSince(@Param("since") Instant since);

    @Query("select count(r) from FixRun r where r.createdAt >= :since")
    long countAllSince(@Param("since") Instant since);

    @Query("select r.id from FixRun r where r.createdAt >= :since")
    List<Long> idsSince(@Param("since") Instant since);

    /** For auto-mode rate-limit gating: count auto runs in the recent window per project. */
    @Query("select count(r) from FixRun r where r.projectId = :projectId "
            + "and r.autoMode = true and r.createdAt >= :since")
    long countAutoRunsSince(@Param("projectId") Long projectId, @Param("since") Instant since);
}
