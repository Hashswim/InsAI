package com.insuranceai.collab.domain;

import com.insuranceai.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * 케이스 활동 로그. UI 피드 + 감사 추적. actorId 는 시스템 이벤트면 null 가능.
 */
@Entity
@Table(name = "timeline_events")
public class TimelineEvent extends BaseEntity {

	@Column(nullable = false)
	private UUID caseId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private TimelineEventType type;

	@Column
	private UUID actorId;

	protected TimelineEvent() {
	}

	public static TimelineEvent of(UUID caseId, TimelineEventType type, UUID actorId) {
		TimelineEvent event = new TimelineEvent();
		event.assignId();
		event.caseId = Objects.requireNonNull(caseId, "caseId");
		event.type = Objects.requireNonNull(type, "type");
		event.actorId = actorId;
		return event;
	}

	public UUID getCaseId() {
		return caseId;
	}

	public TimelineEventType getType() {
		return type;
	}

	public UUID getActorId() {
		return actorId;
	}
}
