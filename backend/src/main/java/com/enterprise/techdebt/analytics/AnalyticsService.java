package com.enterprise.techdebt.analytics;

import com.enterprise.techdebt.domain.FixRunState;
import com.enterprise.techdebt.repo.FixAttemptRepository;
import com.enterprise.techdebt.repo.FixRunRepository;
import com.enterprise.techdebt.repo.IssueRepository;
import com.enterprise.techdebt.repo.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Analytics queries. Sprint 9 perf pass: rollup + metrics counts are answered by
 * GROUP BY queries on the DB side, not by iterating findAll() in the app. The only
 * remaining O(rows) work is MTTR (we need first_seen and updated_at to compute per-issue
 * latency), and that's bounded by the WHERE clause filtering to FIXED/FIXED_EXTERNALLY.
 */
@Service
public class AnalyticsService {

    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final FixRunRepository fixRunRepository;
    private final FixAttemptRepository attemptRepository;

    public AnalyticsService(IssueRepository issueRepository,
                            ProjectRepository projectRepository,
                            FixRunRepository fixRunRepository,
                            FixAttemptRepository attemptRepository) {
        this.issueRepository = issueRepository;
        this.projectRepository = projectRepository;
        this.fixRunRepository = fixRunRepository;
        this.attemptRepository = attemptRepository;
    }

    @Transactional(readOnly = true)
    public List<OrgRollupRow> orgRollup() {
        Map<Long, String> projectNames = new HashMap<>();
        projectRepository.findAll().forEach(p -> projectNames.put(p.getId(), p.getName()));

        List<Object[]> raw = issueRepository.aggregateForRollup();
        List<OrgRollupRow> out = new ArrayList<>(raw.size());
        for (Object[] row : raw) {
            Long projectId = (Long) row[0];
            String source = (String) row[1];
            String severity = (String) row[2];
            String status = (String) row[3];
            long count = ((Number) row[4]).longValue();
            out.add(new OrgRollupRow(projectId,
                    projectNames.getOrDefault(projectId, "?"),
                    source, severity, status, count));
        }
        out.sort(Comparator
                .comparing(OrgRollupRow::projectName)
                .thenComparing(OrgRollupRow::source)
                .thenComparing(OrgRollupRow::severity));
        return out;
    }

    @Transactional(readOnly = true)
    public FixSuccessMetrics fixSuccessMetrics(int sinceDays) {
        Instant since = Instant.now().minus(sinceDays, ChronoUnit.DAYS);

        long total = fixRunRepository.countAllSince(since);
        Map<String, Long> stateCounts = new HashMap<>();
        for (Object[] row : fixRunRepository.countByStateSince(since)) {
            stateCounts.put((String) row[0], ((Number) row[1]).longValue());
        }
        long merged = stateCounts.getOrDefault(FixRunState.MERGED.name(), 0L);
        long failed = stateCounts.getOrDefault(FixRunState.FAILED.name(), 0L);
        long safetyBlocked = stateCounts.getOrDefault(FixRunState.SAFETY_BLOCKED.name(), 0L);
        long prOpened = stateCounts.getOrDefault(FixRunState.PR_OPENED.name(), 0L);

        double successRate = total == 0 ? 0.0 : ((double) merged + prOpened) / total;
        double safetyBlockRate = total == 0 ? 0.0 : (double) safetyBlocked / total;

        Double avgRefix = null;
        List<Long> runIds = fixRunRepository.idsSince(since);
        if (!runIds.isEmpty()) {
            long attemptSum = 0;
            int counted = 0;
            for (Long id : runIds) {
                int n = attemptRepository.findAllByFixRunIdOrderByAttemptNumberAsc(id).size();
                if (n > 0) {
                    attemptSum += n;
                    counted++;
                }
            }
            if (counted > 0) avgRefix = (double) attemptSum / counted;
        }

        Map<String, Long> ingested = new HashMap<>();
        for (Object[] row : issueRepository.countIngestedBySource(since)) {
            ingested.put((String) row[0], ((Number) row[1]).longValue());
        }
        Map<String, Long> openBySource = new HashMap<>();
        for (Object[] row : issueRepository.countOpenBySource()) {
            openBySource.put((String) row[0], ((Number) row[1]).longValue());
        }

        Map<String, Long> mttrAccum = new HashMap<>();
        Map<String, Long> mttrCount = new HashMap<>();
        for (Object[] row : issueRepository.resolvedIssueTimings()) {
            String source = (String) row[0];
            Instant firstSeen = (Instant) row[1];
            Instant updatedAt = (Instant) row[2];
            long secs = updatedAt.getEpochSecond() - firstSeen.getEpochSecond();
            if (secs >= 0) {
                mttrAccum.merge(source, secs, Long::sum);
                mttrCount.merge(source, 1L, Long::sum);
            }
        }
        Map<String, Double> mttrBySource = new HashMap<>();
        for (String src : mttrAccum.keySet()) {
            long c = mttrCount.getOrDefault(src, 0L);
            if (c > 0) mttrBySource.put(src, (double) mttrAccum.get(src) / c);
        }

        return FixSuccessMetrics.builder()
                .since(since)
                .totalRuns(total)
                .mergedRuns(merged)
                .failedRuns(failed)
                .safetyBlockedRuns(safetyBlocked)
                .prOpenedRuns(prOpened)
                .successRate(successRate)
                .safetyBlockRate(safetyBlockRate)
                .averageRefixAttempts(avgRefix)
                .issuesIngestedBySource(ingested)
                .openIssuesBySource(openBySource)
                .mttrSecondsBySource(mttrBySource)
                .notes(List.of(
                        "MTTR = time from issue.first_seen to its last status update for FIXED/FIXED_EXTERNALLY",
                        "successRate counts MERGED + PR_OPENED as successes",
                        "rollup uses native GROUP BY (Sprint 9 perf pass)"))
                .build();
    }
}
