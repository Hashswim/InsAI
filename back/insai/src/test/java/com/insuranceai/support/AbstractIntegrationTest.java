package com.insuranceai.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * HTTP 진입부터 DB 까지 전 계층을 관통하는 통합 테스트의 베이스.
 * MockMvc 로 요청한다.
 *
 * DB: 현재는 test 프로파일의 H2(PostgreSQL 호환 모드).
 * TODO: Docker Desktop / Testcontainers 호환 문제 해소되면 실제 PostgreSQL 컨테이너로 복구.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {
}
