package com.forgeflow.config

import jakarta.persistence.EntityManagerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.transaction.PlatformTransactionManager

@Configuration
@EnableJpaAuditing
class PersistenceConfig {

	@Bean
	fun transactionManager(entityManagerFactory: EntityManagerFactory): PlatformTransactionManager =
		TenantAwareJpaTransactionManager(entityManagerFactory)
}
