package com.enterprise.techdebt.safety;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * One signal's contribution to the overall safety score. Reasons are short,
 * surfaced verbatim in the safety-block UI to explain why a fix was held.
 */
@Value
@Builder
public class SignalResult {
    String signalName;
    int score;
    List<String> reasons;
}
