package com.insuranceai.auth.infrastructure;

import com.insuranceai.auth.application.RefreshTokenStore;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 인메모리 리프레시 토큰 저장소 (단일 인스턴스 전용).
 * TODO: 다중 인스턴스/영속성이 필요해지면 Redis 구현으로 교체.
 */
@Component
public class InMemoryRefreshTokenStore implements RefreshTokenStore {

	private record Entry(UUID accountId, Instant expiresAt) {
	}

	private final Map<String, Entry> store = new ConcurrentHashMap<>();

	@Override
	public void save(String tokenId, UUID accountId, Duration ttl) {
		store.put(tokenId, new Entry(accountId, Instant.now().plus(ttl)));
	}

	@Override
	public Optional<UUID> findAccountId(String tokenId) {
		Entry entry = store.get(tokenId);
		if (entry == null) {
			return Optional.empty();
		}
		if (entry.expiresAt().isBefore(Instant.now())) {
			store.remove(tokenId);
			return Optional.empty();
		}
		return Optional.of(entry.accountId());
	}

	@Override
	public void delete(String tokenId) {
		store.remove(tokenId);
	}
}
