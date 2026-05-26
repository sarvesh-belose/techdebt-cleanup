package com.enterprise.techdebt.api;

import com.enterprise.techdebt.analytics.AnalyticsService;
import com.enterprise.techdebt.analytics.FixSuccessMetrics;
import com.enterprise.techdebt.analytics.OrgRollupRow;
import com.enterprise.techdebt.security.CurrentUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final CurrentUserService currentUserService;

    public AnalyticsController(AnalyticsService analyticsService, CurrentUserService currentUserService) {
        this.analyticsService = analyticsService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/org-rollup")
    public List<OrgRollupRow> orgRollup() {
        currentUserService.require();
        return analyticsService.orgRollup();
    }

    @GetMapping("/metrics")
    public FixSuccessMetrics metrics(@RequestParam(defaultValue = "90") int sinceDays) {
        currentUserService.require();
        return analyticsService.fixSuccessMetrics(sinceDays);
    }
}
