package com.forgeflow.service

import com.forgeflow.context.TenantContext
import com.forgeflow.domain.Order
import com.forgeflow.dto.OrderResponse
import com.forgeflow.exception.ResourceNotFoundException
import com.forgeflow.repository.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class OrderService(
	private val orderRepository: OrderRepository,
) {

	@Transactional(readOnly = true)
	fun list(): List<OrderResponse> =
		orderRepository.findAllByTenantId(TenantContext.getCurrentTenant()).map { it.toResponse() }

	@Transactional(readOnly = true)
	fun get(id: UUID): OrderResponse =
		(
			orderRepository.findByTenantIdAndId(TenantContext.getCurrentTenant(), id)
				?: throw ResourceNotFoundException("Order $id not found")
			).toResponse()

	private fun Order.toResponse() = OrderResponse(
		id = id!!,
		quoteId = quoteId,
		orderNumber = orderNumber,
		customerName = customerName,
		customerEmail = customerEmail,
		totalAmount = totalAmount,
		createdAt = createdAt,
		updatedAt = updatedAt,
	)
}
