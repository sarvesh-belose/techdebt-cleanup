package com.enterprise.techdebt.api;

import com.enterprise.techdebt.api.dto.CreateProjectRequest;
import com.enterprise.techdebt.api.dto.PageResponse;
import com.enterprise.techdebt.api.dto.ProjectResponse;
import com.enterprise.techdebt.api.dto.UpdateProjectRequest;
import com.enterprise.techdebt.security.CurrentUserService;
import com.enterprise.techdebt.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final CurrentUserService currentUserService;

    public ProjectController(ProjectService projectService, CurrentUserService currentUserService) {
        this.projectService = projectService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest req) {
        var project = projectService.create(currentUserService.require(), req);
        return ResponseEntity.created(URI.create("/api/projects/" + project.getId()))
                .body(ProjectResponse.from(project));
    }

    @GetMapping
    public PageResponse<ProjectResponse> list(
            @RequestParam(name = "includeArchived", defaultValue = "false") boolean includeArchived,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        currentUserService.require();
        Page<com.enterprise.techdebt.domain.Project> page = projectService.list(includeArchived, pageable);
        return PageResponse.of(page, ProjectResponse::from);
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable Long id) {
        currentUserService.require();
        return ProjectResponse.from(projectService.getById(id));
    }

    @PutMapping("/{id}")
    public ProjectResponse update(@PathVariable Long id,
                                  @Valid @RequestBody UpdateProjectRequest req) {
        return ProjectResponse.from(projectService.update(currentUserService.require(), id, req));
    }

    @PostMapping("/{id}/archive")
    public ProjectResponse archive(@PathVariable Long id) {
        return ProjectResponse.from(projectService.setArchived(currentUserService.require(), id, true));
    }

    @PostMapping("/{id}/unarchive")
    public ProjectResponse unarchive(@PathVariable Long id) {
        return ProjectResponse.from(projectService.setArchived(currentUserService.require(), id, false));
    }
}
