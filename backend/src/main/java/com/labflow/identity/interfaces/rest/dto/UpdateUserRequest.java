package com.labflow.identity.interfaces.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(max = 80, message = "昵称长度不能超过 80")
        String displayName,

        @Email(message = "邮箱格式不正确")
        @Size(max = 254, message = "邮箱长度不能超过 254")
        String email
) {
}
