package com.insuranceai.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * 계정 애그리게이트 루트.
 *
 * 불변식(테스트로 강제):
 * - email 은 소문자·trim 으로 정규화되어 저장된다
 * - 평문 비밀번호는 보관하지 않는다 (passwordHash 만)
 * - role 미지정 시 CLAIMANT, 상태 ACTIVE
 * - role = ADJUSTER 는 PENDING 으로 생성되어 관리자 승인 후 ACTIVE
 */
@Entity
@Table(
		name = "accounts",
		uniqueConstraints = @UniqueConstraint(name = "uk_accounts_email", columnNames = "email")
)
@EntityListeners(AuditingEntityListener.class)
public class Account {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(nullable = false)
	private String passwordHash;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AccountStatus status;

	@CreatedDate
	@Column(updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	private Instant updatedAt;

	protected Account() {
	}

	public static Account create(String email, String passwordHash, String name, Role role) {
		throw new UnsupportedOperationException("TODO: implement Account.create");
	}

	/** PENDING -> ACTIVE */
	public void activate() {
		throw new UnsupportedOperationException("TODO: implement Account.activate");
	}

	/** ACTIVE -> SUSPENDED */
	public void suspend() {
		throw new UnsupportedOperationException("TODO: implement Account.suspend");
	}

	public void changePassword(String newPasswordHash) {
		throw new UnsupportedOperationException("TODO: implement Account.changePassword");
	}

	/** PENDING/SUSPENDED 이면 BusinessException(FORBIDDEN) */
	public void verifyActive() {
		throw new UnsupportedOperationException("TODO: implement Account.verifyActive");
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getName() {
		return name;
	}

	public Role getRole() {
		return role;
	}

	public AccountStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
