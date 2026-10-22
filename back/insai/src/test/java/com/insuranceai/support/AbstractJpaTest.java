package com.insuranceai.support;

import com.insuranceai.common.config.JpaConfig;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Repository 슬라이스 테스트의 베이스.
 *
 * DB: test 프로파일의 H2(PostgreSQL 호환 모드), JPA Auditing 활성화(JpaConfig).
 * TODO: Testcontainers 복구 시 실제 PostgreSQL 로 전환.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@ActiveProfiles("test")
public abstract class AbstractJpaTest {
}
