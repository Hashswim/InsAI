package com.insuranceai.auth.domain;

/**
 * 계정 상태.
 * ACTIVE  - 로그인 가능
 * PENDING - 승인 대기(사정사 가입 직후). 로그인 불가
 * SUSPENDED - 정지. 로그인 불가
 */
public enum AccountStatus {
	ACTIVE,
	PENDING,
	SUSPENDED
}
