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

/** `GenericContainer` refers to itself in its generics, so Kotlin needs a small subclass here. */
private class RedisContainer : GenericContainer<RedisContainer>("redis:7-alpine")

/**
 * Containers shared by every integration test class in this JVM. They start once and are never
 * stopped by hand; Testcontainers' Ryuk container cleans them up when the JVM exits.
 *
 * Starting them per test class would be much slower and wouldn't isolate anything, because each
 * test registers its own tenant and never touches another test's rows.
 *
 * All three services are real instead of stubbed. The Spring context sets up caching and a Kafka
 * listener anyway, so turning them off here would mean these tests quietly stop covering the cache
 * and event code.
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

	// Confluent's image instead of the `apache/kafka` one used in docker-compose. Testcontainers
	// couldn't start apache/kafka 3.9.0 here (the broker exited with code 1 before it logged the
	// line Testcontainers waits for), and for these tests either broker works.
	val kafka: ConfluentKafkaContainer = ConfluentKafkaContainer("confluentinc/cp-kafka:7.8.0")
		.also { it.start() }
}

/**
 * Starts the real Spring context against the containers above, with the same two-role setup as
 * production (see V1__init_schema.sql). Flyway runs as the container's superuser, and the app
 * connects as the `forgeflow_app` role that migration creates.
 *
 * That split matters: superusers ignore row-level security, so if the app also connected as one,
 * these tests would still pass even with RLS completely broken.
 *
 * `@AutoConfigureMockMvc` is what puts the real Spring Security filter chain in front of MockMvc.
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

	/**
	 * Polls [condition] until it returns a non-null result or [timeoutMs] passes, then returns it —
	 * or fails the test if it never did.
	 *
	 * For asserting on the Kafka consumer's side effects: `OrderEventListener` reads the topic on
	 * its own thread, so there's no way to know from the test's thread exactly when it has finished
	 * — the record it wrote just has to be waited for.
	 */
	protected fun <T> awaitUntil(timeoutMs: Long = 5000, intervalMs: Long = 200, condition: () -> T?): T {
		val deadline = System.currentTimeMillis() + timeoutMs
		while (System.currentTimeMillis() < deadline) {
			condition()?.let { return it }
			Thread.sleep(intervalMs)
		}
		return condition() ?: error("Condition was not met within ${timeoutMs}ms")
	}

	/** Registers a new tenant with a unique slug and returns its admin's token. */
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
			// If the broker is unreachable the producer waits 60 seconds by default, which would
			// stall a test that doesn't even check Kafka. Ten seconds is plenty here.
			registry.add("spring.kafka.producer.properties.max.block.ms") { "10000" }
		}
	}
}
