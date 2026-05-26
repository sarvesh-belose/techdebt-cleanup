package com.enterprise.techdebt.api;

import com.enterprise.techdebt.api.dto.KillSwitchRequest;
import com.enterprise.techdebt.automode.SystemSettingsService;
import com.enterprise.techdebt.security.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Org-wide system controls. Today: just the auto-mode kill switch. Future tunables
 * (auto-merge defaults, allowed Copilot models, etc.) live here too.
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SystemSettingsService settings;
    private final CurrentUserService currentUserService;

    public SystemController(SystemSettingsService settings, CurrentUserService currentUserService) {
        this.settings = settings;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/auto-mode-kill-switch")
    public Map<String, Boolean> getKillSwitch() {
        currentUserService.require();
        return Map.of("killed", settings.isAutoModeKilled());
    }

    @PostMapping("/auto-mode-kill-switch")
    public Map<String, Boolean> setKillSwitch(@Valid @RequestBody KillSwitchRequest req) {
        var actor = currentUserService.require();
        settings.setAutoModeKilled(actor, Boolean.TRUE.equals(req.killed()));
        return Map.of("killed", settings.isAutoModeKilled());
    }
}
