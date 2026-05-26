package com.enterprise.techdebt.repo;

import com.enterprise.techdebt.domain.FixAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FixAttemptRepository extends JpaRepository<FixAttempt, Long> {
    List<FixAttempt> findAllByFixRunIdOrderByAttemptNumberAsc(Long fixRunId);
}
