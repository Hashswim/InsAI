package com.insuranceai.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * HTTP 진입부터 DB 까지 전 계층을 관통하는 통합 테스트의 베이스.
 * MockMvc 로 요청하며, 각 테스트는 트랜잭션 안에서 실행되고 종료 시 롤백된다
 * (공유 H2 를 오염시키지 않기 위함 — MockMvc 는 같은 스레드에서 디스패치되므로 서비스 트랜잭션이 합류한다).
 *
 * DB: 현재는 test 프로파일의 H2(PostgreSQL 호환 모드).
 * TODO: Docker Desktop / Testcontainers 호환 문제 해소되면 실제 PostgreSQL 컨테이너로 복구.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class AbstractIntegrationTest {
}
