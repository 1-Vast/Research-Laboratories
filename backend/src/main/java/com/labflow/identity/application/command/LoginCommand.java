package com.labflow.identity.application.command;

public record LoginCommand(String username, String rawPassword, String clientIp, String userAgent) {
}
