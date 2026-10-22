package com.insuranceai.auth.application;

public record LoginCommand(String email, String rawPassword) {
}
