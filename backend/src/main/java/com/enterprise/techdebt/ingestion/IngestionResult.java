package com.enterprise.techdebt.ingestion;

import lombok.Value;

@Value
public class IngestionResult {
    long projectId;
    String source;
    int created;
    int updated;
    int resolvedExternally;
    int total;
}
