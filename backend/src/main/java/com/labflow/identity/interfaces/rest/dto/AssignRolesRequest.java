package com.labflow.identity.interfaces.rest.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AssignRolesRequest(
        @NotEmpty(message = "至少需要一个角色") List<String> roles
) {
}
