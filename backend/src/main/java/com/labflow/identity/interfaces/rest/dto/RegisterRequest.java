package com.labflow.identity.interfaces.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "[A-Za-z0-9_.-]{3,32}", message = "用户名为 3-32 位字母、数字、下划线、点或短横线")
        String username,

        @NotBlank(message = "密码不能为空")
        String password,

        @Size(max = 80, message = "昵称长度不能超过 80")
        String displayName,

        @Email(message = "邮箱格式不正确")
        @Size(max = 254, message = "邮箱长度不能超过 254")
        String email
) {
}
