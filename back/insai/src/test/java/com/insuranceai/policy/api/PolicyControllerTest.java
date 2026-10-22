package com.insuranceai.policy.api;

import com.insuranceai.common.config.SecurityConfig;
import com.insuranceai.policy.application.ListMyPoliciesService;
import com.insuranceai.policy.application.PolicyView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PolicyController.class)
@Import(SecurityConfig.class)
class PolicyControllerTest {

	@Autowired MockMvc mvc;

	@MockitoBean ListMyPoliciesService listMyPoliciesService;

	private static final String USER = "aaaa1111-1111-1111-1111-111111111111";

	@Test
	@DisplayName("GET /policies -> 200, 내 보험 목록")
	void myPolicies() throws Exception {
		given(listMyPoliciesService.list(any())).willReturn(List.of(new PolicyView(
				UUID.randomUUID(), "한화생명", "평생든든 종신보험", "HL-1",
				"WHOLE_LIFE", "종신보험", "홍길동",
				LocalDate.of(2016, 3, 10), LocalDate.of(2016, 3, 10), null,
				100_000_000L, 182_000L, "사망 1억", "ACTIVE", "정상")));

		mvc.perform(get("/policies").header("X-User-Id", USER))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].insurerName").value("한화생명"))
				.andExpect(jsonPath("$.data[0].planTypeLabel").value("종신보험"))
				.andExpect(jsonPath("$.data[0].statusLabel").value("정상"));
	}

	@Test
	@DisplayName("GET /policies: X-User-Id 없으면 400")
	void missingHeader() throws Exception {
		mvc.perform(get("/policies"))
				.andExpect(status().isBadRequest());
	}
}
