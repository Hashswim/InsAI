package com.insuranceai.support;

import com.insuranceai.common.config.JpaConfig;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Repository 슬라이스 테스트의 베이스. 실제 PostgreSQL(Testcontainers) 에 붙는다.
 * (임베디드 DB 로 치환하지 않도록 replace = NONE, JPA Auditing 활성화를 위해 JpaConfig import)
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@ActiveProfiles("it")
public abstract class AbstractJpaTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestPostgres.registerProperties(registry);
	}
}
