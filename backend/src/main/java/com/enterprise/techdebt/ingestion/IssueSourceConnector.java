package com.enterprise.techdebt.ingestion;

import com.enterprise.techdebt.domain.IssueSource;
import com.enterprise.techdebt.domain.ScanSourceConfig;

import java.util.List;

/**
 * Pluggable scanner connector. Implementations fetch findings from an external
 * system (Veracode, SonarQube, …) and return them as normalized {@link RawIssue}s.
 *
 * Implementations should:
 * <ul>
 *   <li>Be safe to call repeatedly — the ingestion service handles dedup.</li>
 *   <li>Respect rate limits via the underlying HTTP client (timeouts, retries).</li>
 *   <li>Resolve secrets through the configured SecretStore — never accept raw tokens.</li>
 *   <li>Throw {@link ConnectorException} for upstream errors; partial results are not allowed
 *       unless explicitly streamed.</li>
 * </ul>
 */
public interface IssueSourceConnector {

    IssueSource source();

    List<RawIssue> fetchIssues(ScanSourceConfig cfg);
}
