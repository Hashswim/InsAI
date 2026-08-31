package com.insuranceai.ai.application;

import java.util.function.Function;

/** 테스트용 LlmClient — 고정 응답 또는 예외를 돌려준다. */
public final class FakeLlmClient implements LlmClient {

	private final Function<LlmRequest, String> behavior;

	private FakeLlmClient(Function<LlmRequest, String> behavior) {
		this.behavior = behavior;
	}

	public static FakeLlmClient returning(String response) {
		return new FakeLlmClient(req -> response);
	}

	public static FakeLlmClient throwing(RuntimeException e) {
		return new FakeLlmClient(req -> {
			throw e;
		});
	}

	@Override
	public String complete(LlmRequest request) {
		return behavior.apply(request);
	}
}
