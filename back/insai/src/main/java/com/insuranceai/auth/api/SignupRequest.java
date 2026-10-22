package com.insuranceai.auth.api;

import com.insuranceai.auth.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * role 은 선택 (미지정 시 CLAIMANT).
 */
public record SignupRequest(
		@NotBlank @Email String email,
		@NotBlank @Size(min = 8, max = 100) String password,
		@NotBlank String name,
		Role role
) {
}
