package com.enterprise.techdebt.automode;

import com.enterprise.techdebt.domain.IssueSource;
import com.enterprise.techdebt.domain.Severity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AutoModePolicyTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void emptyPolicyDeniesEverything() {
        AutoModePolicy p = AutoModePolicy.empty();
        assertThat(p.isEmpty()).isTrue();
        for (IssueSource s : IssueSource.values()) {
            for (Severity sev : Severity.values()) {
                assertThat(p.isEligible(s, sev)).isFalse();
            }
        }
    }

    @Test
    void nullJsonParsesToEmpty() {
        AutoModePolicy p = AutoModePolicy.fromJson(null, mapper);
        assertThat(p.isEmpty()).isTrue();
    }

    @Test
    void allowsOnlySpecifiedSeveritiesPerSource() {
        AutoModePolicy p = new AutoModePolicy(Map.of(
                IssueSource.VERACODE_SCA, EnumSet.of(Severity.CRITICAL, Severity.HIGH)));
        assertThat(p.isEligible(IssueSource.VERACODE_SCA, Severity.CRITICAL)).isTrue();
        assertThat(p.isEligible(IssueSource.VERACODE_SCA, Severity.HIGH)).isTrue();
        assertThat(p.isEligible(IssueSource.VERACODE_SCA, Severity.MEDIUM)).isFalse();
        assertThat(p.isEligible(IssueSource.VERACODE_SAST, Severity.CRITICAL)).isFalse();
    }

    @Test
    void roundTripsThroughJson() throws Exception {
        AutoModePolicy original = new AutoModePolicy(Map.of(
                IssueSource.VERACODE_SCA, EnumSet.of(Severity.CRITICAL),
                IssueSource.SONARQUBE, EnumSet.of(Severity.LOW)));
        String json = original.toJson(mapper);
        assertThat(json).isNotBlank();
        AutoModePolicy parsed = AutoModePolicy.fromJson(json, mapper);
        assertThat(parsed.isEligible(IssueSource.VERACODE_SCA, Severity.CRITICAL)).isTrue();
        assertThat(parsed.isEligible(IssueSource.SONARQUBE, Severity.LOW)).isTrue();
        assertThat(parsed.isEligible(IssueSource.VERACODE_SAST, Severity.CRITICAL)).isFalse();
    }

    @Test
    void invalidJsonParsesToEmpty() {
        AutoModePolicy p = AutoModePolicy.fromJson("{not valid", mapper);
        assertThat(p.isEmpty()).isTrue();
    }
}
