package com.insuranceai.collab.application;

import com.insuranceai.collab.domain.TimelineEventType;

import java.time.Instant;
import java.util.UUID;

public record TimelineEntry(TimelineEventType type, UUID actorId, Instant at) {
}
