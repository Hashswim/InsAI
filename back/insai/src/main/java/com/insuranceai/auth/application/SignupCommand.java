package com.insuranceai.auth.application;

import com.insuranceai.auth.domain.Role;

/**
 * role 이 null 이면 CLAIMANT 로 간주한다.
 */
public record SignupCommand(String email, String rawPassword, String name, Role role) {
}
