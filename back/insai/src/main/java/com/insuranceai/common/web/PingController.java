package com.insuranceai.common.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * 애플리케이션이 살아있는지 확인하는 최소 엔드포인트. 실제 도메인 API 로 대체되기 전까지의 스켈레톤.
 */
@RestController
@RequestMapping("/api")
public class PingController {

	@GetMapping("/ping")
	public ApiResponse<Map<String, Object>> ping() {
		return ApiResponse.ok(Map.of(
				"service", "insai",
				"time", Instant.now().toString()
		));
	}
}
