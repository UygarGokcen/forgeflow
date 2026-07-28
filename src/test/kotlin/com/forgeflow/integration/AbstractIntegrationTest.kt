package com.forgeflow.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.forgeflow.dto.AuthResponse
import com.forgeflow.dto.RegisterTenantRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.junit.jupiter.api.Tag
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

/**
 * Single Postgres container shared across every integration test class in this JVM (Testcontainers'
 * "singleton container" pattern) — started once, never explicitly stopped; the Ryuk reaper cleans it
 * up when the JVM exits. Re-creating a container per test class would make the suite far slower for
 * no isolation benefit, since each test already registers its own tenant and never touches another
 * test's rows.
 */
private object SharedPostgres {
	val container: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
		.withDatabaseName("forgeflow")
		.withUsername("forgeflow")
		.withPassword("forgeflow")
		.also { it.start() }
}

/**
 * Boots the real Spring context against the container above, using the same admin/app role split
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
		fun configureDatasources(registry: DynamicPropertyRegistry) {
			val container = SharedPostgres.container

			registry.add("spring.flyway.url") { container.jdbcUrl }
			registry.add("spring.flyway.user") { container.username }
			registry.add("spring.flyway.password") { container.password }

			registry.add("spring.datasource.url") { container.jdbcUrl }
			registry.add("spring.datasource.username") { "forgeflow_app" }
			registry.add("spring.datasource.password") { "forgeflow_app" }
		}
	}
}
