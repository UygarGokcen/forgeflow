package com.forgeflow.integration

import com.forgeflow.context.TenantContext
import com.forgeflow.domain.UnitOfMeasure
import com.forgeflow.dto.CreateMaterialRequest
import com.forgeflow.dto.MaterialResponse
import com.forgeflow.repository.MaterialRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal

/**
 * Reproduces the lost update the `@Version` columns exist to stop, against real Postgres: one
 * transaction reads a material, a second one changes its stock and commits, then the first one
 * writes back what it read. Without a version check that write would silently put the old stock
 * level back.
 */
class OptimisticLockingIntegrationTest : AbstractIntegrationTest() {

	@Autowired
	lateinit var transactionManager: PlatformTransactionManager

	@Autowired
	lateinit var materialRepository: MaterialRepository

	@Test
	fun `a stale write is rejected instead of overwriting a concurrent stock change`() {
		val tenant = registerTenant("optimistic")
		val json = mockMvc.perform(
			post("/api/v1/materials")
				.header("Authorization", "Bearer ${tenant.token}")
				.contentType(MediaType.APPLICATION_JSON)
				.content(
					objectMapper.writeValueAsString(
						CreateMaterialRequest(
							sku = "SHEET-OPT",
							name = "Steel sheet",
							unitOfMeasure = UnitOfMeasure.KILOGRAM,
							stockQuantity = BigDecimal("100"),
							reorderLevel = BigDecimal("10"),
						),
					),
				),
		).andExpect(status().isCreated).andReturn().response.contentAsString
		val materialId = objectMapper.readValue(json, MaterialResponse::class.java).id

		val outer = TransactionTemplate(transactionManager)
		val concurrent = TransactionTemplate(transactionManager).apply {
			propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
		}

		TenantContext.runWithTenant(tenant.tenantId) {
			assertThrows(ObjectOptimisticLockingFailureException::class.java) {
				outer.executeWithoutResult {
					val stale = materialRepository.findByTenantIdAndId(tenant.tenantId, materialId)!!

					// Another request draws stock and commits while this one is still working.
					concurrent.executeWithoutResult {
						val fresh = materialRepository.findByTenantIdAndId(tenant.tenantId, materialId)!!
						fresh.stockQuantity = BigDecimal("60")
						materialRepository.saveAndFlush(fresh)
					}

					stale.name = "Renamed sheet"
					materialRepository.saveAndFlush(stale)
				}
			}

			// The concurrent stock change survived; the stale write didn't undo it.
			val current = materialRepository.findByTenantIdAndId(tenant.tenantId, materialId)!!
			assertEquals(0, BigDecimal("60").compareTo(current.stockQuantity))
			assertEquals("Steel sheet", current.name)
		}
	}
}
