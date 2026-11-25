package com.labflow.identity.interfaces.rest.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AssignPermissionsRequest(
        @NotNull(message = "permissions 不能为空") List<String> permissions
) {
}
