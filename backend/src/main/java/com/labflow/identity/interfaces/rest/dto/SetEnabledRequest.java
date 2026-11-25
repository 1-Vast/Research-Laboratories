package com.labflow.identity.interfaces.rest.dto;

import jakarta.validation.constraints.NotNull;

public record SetEnabledRequest(
        @NotNull(message = "enabled 不能为空") Boolean enabled
) {
}
