package com.enterprise.techdebt.security;

import com.enterprise.techdebt.domain.ProjectPermission;
import com.enterprise.techdebt.domain.ProjectRole;
import com.enterprise.techdebt.repo.ProjectPermissionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {

    private final ProjectPermissionRepository permissionRepository;
    private final boolean devBypass;

    public AuthorizationService(ProjectPermissionRepository permissionRepository,
                                @Value("${techdebt.security.dev-bypass:false}") boolean devBypass) {
        this.permissionRepository = permissionRepository;
        this.devBypass = devBypass;
    }

    public void requireProjectRole(CurrentUser user, Long projectId, ProjectRole required) {
        if (devBypass) {
            return;
        }
        ProjectPermission perm = permissionRepository.findByProjectIdAndUserId(projectId, user.userId())
                .orElseThrow(() -> new AccessDeniedException(
                        "User " + user.upn() + " has no permission on project " + projectId));
        if (!perm.getRole().atLeast(required)) {
            throw new AccessDeniedException(
                    "User " + user.upn() + " role " + perm.getRole() + " < required " + required);
        }
    }

    public boolean hasProjectRole(CurrentUser user, Long projectId, ProjectRole required) {
        if (devBypass) {
            return true;
        }
        return permissionRepository.findByProjectIdAndUserId(projectId, user.userId())
                .map(p -> p.getRole().atLeast(required))
                .orElse(false);
    }
}
