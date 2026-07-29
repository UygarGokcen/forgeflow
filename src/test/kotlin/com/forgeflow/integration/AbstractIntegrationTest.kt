package com.forgeflow.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.forgeflow.dto.AuthResponse
import com.forgeflow.dto.RegisterTenantRequest
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.ConfluentKafkaContainer
import java.util.UUID

/** `GenericContainer` is self-referentially generic; Kotlin needs a concrete subclass to use it. */
private class RedisContainer : GenericContainer<RedisContainer>("redis:7-alpine")

/**
 * Containers shared across every integration test class in this JVM (Testcontainers' "singleton
 * container" pattern) — started once, never explicitly stopped; the Ryuk reaper cleans them up when
 * the JVM exits. Re-creating them per test class would make the suite far slower for no isolation
 * benefit, since each test registers its own tenant and never touches another test's rows.
 *
 * All three backing services run for real rather than being stubbed out: the app's Spring context
 * wires up caching and a Kafka listener regardless, and disabling them here would mean these tests
 * quietly stop covering the cache and event-publishing paths they're meant to exercise.
 */
private object SharedContainers {

	val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
		.withDatabaseName("forgeflow")
		.withUsername("forgeflow")
		.withPassword("forgeflow")
		.also { it.start() }

	val redis: RedisContainer = RedisContainer()
		.withExposedPorts(6379)
		.also { it.start() }

	// Confluent's KRaft image rather than the `apache/kafka` one used in docker-compose:
	// Testcontainers' apache/kafka support couldn't bring 3.9.0 up here (the broker exited 1 before
	// ever logging "Transitioning from RECOVERY to RUNNING"), and the broker is interchangeable for
	// the purposes of these tests.
	val kafka: ConfluentKafkaContainer = ConfluentKafkaContainer("confluentinc/cp-kafka:7.8.0")
		.also { it.start() }
}

/**
 * Boots the real Spring context against the containers above, using the same admin/app role split
 * as production (see V1__init_schema.sql): Flyway migrates as the container's bootstrap superuser,
 * while the application datasource connects as the unprivileged `forgeflow_app` role that migration
 * creates — otherwise these tests would pass even if row-level security were silently broken, since
 * a superuser bypasses RLS unconditionally.
 *
 * `@AutoConfigureMockMvc` (rather than a hand-built `MockMvcBuilders...apply(springSecurity())`) is
 * what wires the real Spring Security filter chain into MockMvc here.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
abstract class AbstractIntegrationTest {

	@Autowired
	protected lateinit var mockMvc: MockMvc

	@Autowired
	lateinit var objectMapper: ObjectMapper

	/** Registers a fresh tenant (unique slug per call) and returns its admin's bearer token. */
	protected fun registerTenant(slugPrefix: String): AuthResponse {
		val slug = "$slugPrefix-${UUID.randomUUID().toString().take(8)}"
		val request = RegisterTenantRequest(
			tenantName = "Test Tenant $slug",
			tenantSlug = slug,
			adminFullName = "Test Admin",
			adminEmail = "admin@$slug.test",
			adminPassword = "supersecret1",
		)
		val json = mockMvc.perform(
			MockMvcRequestBuilders.post("/api/v1/auth/register-tenant")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)),
		).andReturn().response.contentAsString
		return objectMapper.readValue(json, AuthResponse::class.java)
	}

	companion object {
		@DynamicPropertySource
		@JvmStatic
		fun configureBackingServices(registry: DynamicPropertyRegistry) {
			val postgres = SharedContainers.postgres

			registry.add("spring.flyway.url") { postgres.jdbcUrl }
			registry.add("spring.flyway.user") { postgres.username }
			registry.add("spring.flyway.password") { postgres.password }

			registry.add("spring.datasource.url") { postgres.jdbcUrl }
			registry.add("spring.datasource.username") { "forgeflow_app" }
			registry.add("spring.datasource.password") { "forgeflow_app" }

			registry.add("spring.data.redis.host") { SharedContainers.redis.host }
			registry.add("spring.data.redis.port") { SharedContainers.redis.getMappedPort(6379) }

			registry.add("spring.kafka.bootstrap-servers") { SharedContainers.kafka.bootstrapServers }
			// Without this the producer would block on its default 60s metadata timeout if the
			// broker were ever unreachable, stalling a test that doesn't assert on Kafka at all.
			registry.add("spring.kafka.producer.properties.max.block.ms") { "10000" }
		}
	}
}
