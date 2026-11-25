package com.labflow.identity.application.command;

public record RegisterCommand(String username, String rawPassword, String email, String displayName) {
}
