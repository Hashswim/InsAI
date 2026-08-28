package com.insuranceai.auth.application;

/**
 * 로그인/갱신 결과. accessToken 은 JWT, refreshToken 은 불투명 문자열.
 */
public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {
}
