package com.enterprise.techdebt.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record StartFixRunRequest(
        @NotNull @NotEmpty List<Long> issueIds,
        Boolean autoMode
) {
}
