package com.insuranceai.auth.domain;

import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * 계정 애그리게이트 루트.
 *
 * 불변식:
 * - email 은 소문자·trim 으로 정규화되어 저장된다
 * - 평문 비밀번호는 보관하지 않는다 (passwordHash 만)
 * - role 미지정 시 CLAIMANT / ACTIVE
 * - role = ADJUSTER 는 PENDING 으로 생성되어 관리자 승인 후 ACTIVE
 *
 * 식별자는 애플리케이션이 생성한다(UUID). Persistable 로 신규 여부를 알려 persist 경로를 태운다.
 */
@Entity
@Table(
		name = "accounts",
		uniqueConstraints = @UniqueConstraint(name = "uk_accounts_email", columnNames = "email")
)
@EntityListeners(AuditingEntityListener.class)
public class Account implements Persistable<UUID> {

	@Id
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

	@Transient
	private boolean isNew = false;

	protected Account() {
	}

	public static Account create(String email, String passwordHash, String name, Role role) {
		Account account = new Account();
		account.id = UUID.randomUUID();
		account.email = normalizeEmail(email);
		account.passwordHash = requireText(passwordHash, "비밀번호 해시");
		account.name = requireText(name, "이름");
		account.role = role == null ? Role.CLAIMANT : role;
		account.status = account.role == Role.ADJUSTER ? AccountStatus.PENDING : AccountStatus.ACTIVE;
		account.isNew = true;
		return account;
	}

	/** PENDING -> ACTIVE */
	public void activate() {
		if (status != AccountStatus.PENDING) {
			throw new BusinessException(ErrorCode.CONFLICT, "승인 대기 상태가 아닙니다.");
		}
		this.status = AccountStatus.ACTIVE;
	}

	/** -> SUSPENDED */
	public void suspend() {
		this.status = AccountStatus.SUSPENDED;
	}

	public void changePassword(String newPasswordHash) {
		this.passwordHash = requireText(newPasswordHash, "비밀번호 해시");
	}

	/** ACTIVE 가 아니면 BusinessException(FORBIDDEN) */
	public void verifyActive() {
		if (status != AccountStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "로그인할 수 없는 계정 상태입니다.");
		}
	}

	private static String normalizeEmail(String email) {
		if (email == null || email.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "이메일은 필수입니다.");
		}
		return email.trim().toLowerCase();
	}

	private static String requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, field + "은(는) 필수입니다.");
		}
		return value.trim();
	}

	@PostLoad
	@PostPersist
	void markNotNew() {
		this.isNew = false;
	}

	@Override
	public UUID getId() {
		return id;
	}

	@Override
	public boolean isNew() {
		return isNew;
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
