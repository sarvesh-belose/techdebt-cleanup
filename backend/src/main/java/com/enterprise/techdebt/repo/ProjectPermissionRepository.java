package com.enterprise.techdebt.repo;

import com.enterprise.techdebt.domain.ProjectPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectPermissionRepository extends JpaRepository<ProjectPermission, Long> {
    Optional<ProjectPermission> findByProjectIdAndUserId(Long projectId, Long userId);

    List<ProjectPermission> findAllByUserId(Long userId);

    List<ProjectPermission> findAllByProjectId(Long projectId);
}
