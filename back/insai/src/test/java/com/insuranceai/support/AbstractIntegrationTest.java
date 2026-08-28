package com.insuranceai.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * HTTP 진입부터 DB 까지 전 계층을 관통하는 통합 테스트의 베이스.
 * MockMvc 로 요청하고, 실제 PostgreSQL(Testcontainers) 에 붙는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
public abstract class AbstractIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestPostgres.registerProperties(registry);
	}
}
