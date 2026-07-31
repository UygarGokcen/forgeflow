package com.forgeflow.service

import com.forgeflow.context.TenantContext
import com.forgeflow.domain.Order
import com.forgeflow.domain.OrderStatus
import com.forgeflow.dto.OrderResponse
import com.forgeflow.exception.InvalidOrderStatusTransitionException
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
	fun get(id: UUID): OrderResponse = findOwned(id).toResponse()

	@Transactional
	fun updateStatus(id: UUID, newStatus: OrderStatus): OrderResponse {
		val order = findOwned(id)

		val allowedNextStatuses = ALLOWED_TRANSITIONS.getValue(order.status)
		if (newStatus !in allowedNextStatuses) {
			throw InvalidOrderStatusTransitionException(order.status.name, newStatus.name)
		}

		order.status = newStatus
		// Flush here so the response carries the new updatedAt. The auditing listener only sets
		// it during flush, so without this we would return the value from before the update.
		return orderRepository.saveAndFlush(order).toResponse()
	}

	private fun findOwned(id: UUID): Order =
		orderRepository.findByTenantIdAndId(TenantContext.getCurrentTenant(), id)
			?: throw ResourceNotFoundException("Order $id not found")

	private fun Order.toResponse() = OrderResponse(
		id = id!!,
		quoteId = quoteId,
		orderNumber = orderNumber,
		customerName = customerName,
		customerEmail = customerEmail,
		totalAmount = totalAmount,
		status = status,
		createdAt = createdAt,
		updatedAt = updatedAt,
	)

	companion object {
		/**
		 * Same idea as the quote status machine: an explicit map of what can follow what, instead
		 * of a free-text column. Cancellation is only allowed before the order has shipped — once
		 * material has left the building there is nothing left to cancel.
		 */
		private val ALLOWED_TRANSITIONS: Map<OrderStatus, Set<OrderStatus>> = mapOf(
			OrderStatus.CONFIRMED to setOf(OrderStatus.IN_PRODUCTION, OrderStatus.CANCELLED),
			OrderStatus.IN_PRODUCTION to setOf(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
			OrderStatus.SHIPPED to setOf(OrderStatus.DELIVERED),
			OrderStatus.DELIVERED to emptySet(),
			OrderStatus.CANCELLED to emptySet(),
		)
	}
}
