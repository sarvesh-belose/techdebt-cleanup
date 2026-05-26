package com.enterprise.techdebt.api.dto;

import jakarta.validation.constraints.NotNull;

public record KillSwitchRequest(@NotNull Boolean killed) {
}
