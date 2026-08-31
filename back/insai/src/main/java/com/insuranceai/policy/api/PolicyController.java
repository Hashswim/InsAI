package com.insuranceai.policy.api;

import com.insuranceai.common.web.ApiResponse;
import com.insuranceai.policy.application.ListMyPoliciesService;
import com.insuranceai.policy.application.PolicyView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 청구인이 가입한 보험 조회. 행위자 식별은 X-User-Id 헤더.
 */
@RestController
public class PolicyController {

	private final ListMyPoliciesService listMyPoliciesService;

	public PolicyController(ListMyPoliciesService listMyPoliciesService) {
		this.listMyPoliciesService = listMyPoliciesService;
	}

	@GetMapping("/policies")
	public ApiResponse<List<PolicyView>> myPolicies(@RequestHeader("X-User-Id") UUID userId) {
		return ApiResponse.ok(listMyPoliciesService.list(userId));
	}
}
