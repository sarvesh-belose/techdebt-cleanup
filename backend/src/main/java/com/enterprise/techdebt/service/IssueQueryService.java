package com.enterprise.techdebt.service;

import com.enterprise.techdebt.domain.Issue;
import com.enterprise.techdebt.domain.IssueSource;
import com.enterprise.techdebt.domain.IssueStatus;
import com.enterprise.techdebt.domain.Severity;
import com.enterprise.techdebt.repo.IssueRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class IssueQueryService {

    private final IssueRepository issueRepository;

    public IssueQueryService(IssueRepository issueRepository) {
        this.issueRepository = issueRepository;
    }

    @Transactional(readOnly = true)
    public Page<Issue> list(long projectId, IssueQuery q, Pageable pageable) {
        return issueRepository.findAll(buildSpec(projectId, q), pageable);
    }

    private Specification<Issue> buildSpec(long projectId, IssueQuery q) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("projectId"), projectId));
            if (q.source != null) p.add(cb.equal(root.get("source"), q.source));
            if (q.severity != null) p.add(cb.equal(root.get("severity"), q.severity));
            if (q.status != null) p.add(cb.equal(root.get("status"), q.status));
            if (q.filePathContains != null && !q.filePathContains.isBlank()) {
                p.add(cb.like(cb.lower(root.get("filePath")), "%" + q.filePathContains.toLowerCase() + "%"));
            }
            if (q.cve != null && !q.cve.isBlank()) {
                p.add(cb.equal(cb.upper(root.get("cve")), q.cve.toUpperCase()));
            }
            if (q.dateFrom != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("firstSeen"), q.dateFrom));
            }
            if (q.dateTo != null) {
                p.add(cb.lessThanOrEqualTo(root.get("firstSeen"), q.dateTo));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    public static class IssueQuery {
        public IssueSource source;
        public Severity severity;
        public IssueStatus status;
        public String filePathContains;
        public String cve;
        public Instant dateFrom;
        public Instant dateTo;
    }
}
