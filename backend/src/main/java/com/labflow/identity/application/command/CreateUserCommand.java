package com.labflow.identity.application.command;

import java.util.List;

public record CreateUserCommand(String username, String rawPassword, String displayName, String email,
                                List<String> roleCodes) {
}
