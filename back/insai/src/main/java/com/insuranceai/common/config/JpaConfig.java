package com.insuranceai.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 활성화 (@CreatedDate / @LastModifiedDate).
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
