package com.labflow.identity.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateRoleRequest(
        @NotBlank(message = "角色名称不能为空")
        @Size(max = 80, message = "角色名称长度不能超过 80")
        String name,

        @Size(max = 255, message = "描述长度不能超过 255")
        String description
) {
}
