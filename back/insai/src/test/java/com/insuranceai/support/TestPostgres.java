package com.insuranceai.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 전체 테스트가 공유하는 단일 PostgreSQL 컨테이너.
 * JVM 당 한 번 기동하고 재사용한다(스프링 컨텍스트 캐시와 무관하게 컨테이너는 하나).
 */
public final class TestPostgres {

	public static final PostgreSQLContainer<?> INSTANCE =
			new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
					.withDatabaseName("insai")
					.withUsername("insai")
					.withPassword("insai");

	static {
		INSTANCE.start();
	}

	private TestPostgres() {
	}

	public static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", INSTANCE::getJdbcUrl);
		registry.add("spring.datasource.username", INSTANCE::getUsername);
		registry.add("spring.datasource.password", INSTANCE::getPassword);
		registry.add("spring.datasource.driver-class-name", INSTANCE::getDriverClassName);
	}
}
