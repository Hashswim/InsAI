package com.insuranceai.auth.application;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * 리프레시 토큰 저장 포트. 구현: 지금은 인메모리, 이후 Redis.
 * tokenId 는 리프레시 토큰의 고유 식별자(불투명 문자열).
 */
public interface RefreshTokenStore {

	void save(String tokenId, UUID accountId, Duration ttl);

	Optional<UUID> findAccountId(String tokenId);

	void delete(String tokenId);
}
