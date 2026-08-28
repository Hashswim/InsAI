package com.insuranceai.common.domain;

import com.insuranceai.common.error.BusinessException;
import com.insuranceai.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * 애플리케이션 생성 UUID 식별자 + JPA Auditing 공통 베이스.
 * 하위 엔티티는 정적 팩터리에서 {@link #assignId()} 를 호출해 id 를 채우고 신규 표시한다.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity implements Persistable<UUID> {

	@Id
	private UUID id;

	@CreatedDate
	@Column(updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	private Instant updatedAt;

	@Transient
	private boolean isNew = false;

	protected BaseEntity() {
	}

	protected final void assignId() {
		this.id = UUID.randomUUID();
		this.isNew = true;
	}

	/** 공백/누락이면 BusinessException(INVALID_INPUT), 아니면 trim 한 값을 돌려준다. */
	protected static String requireText(String value, String fieldLabel) {
		if (value == null || value.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, fieldLabel + "은(는) 필수입니다.");
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

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
