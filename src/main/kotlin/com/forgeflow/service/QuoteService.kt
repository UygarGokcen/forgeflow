package com.forgeflow.service

import com.forgeflow.context.CurrentUser
import com.forgeflow.context.TenantContext
import com.forgeflow.domain.Order
import com.forgeflow.domain.PricingStrategyType
import com.forgeflow.domain.Quote
import com.forgeflow.domain.QuoteLineItem
import com.forgeflow.domain.QuoteStatus
import com.forgeflow.dto.AddQuoteLineItemRequest
import com.forgeflow.dto.CreateQuoteRequest
import com.forgeflow.dto.QuoteLineItemResponse
import com.forgeflow.dto.QuoteResponse
import com.forgeflow.event.OrderConvertedEvent
import com.forgeflow.exception.EmptyQuoteException
import com.forgeflow.exception.InvalidQuoteStatusTransitionException
import com.forgeflow.exception.QuoteNotEditableException
import com.forgeflow.exception.ResourceNotFoundException
import com.forgeflow.repository.OrderRepository
import com.forgeflow.repository.QuoteLineItemRepository
import com.forgeflow.repository.QuoteRepository
import com.forgeflow.service.pricing.PricingContext
import com.forgeflow.service.pricing.PricingStrategyResolver
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

private val QUOTE_NUMBER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")

/** The status changes a quote is allowed to make. Anything not listed here is rejected. */
private val ALLOWED_TRANSITIONS: Map<QuoteStatus, Set<QuoteStatus>> = mapOf(
	QuoteStatus.DRAFT to setOf(QuoteStatus.APPROVED, QuoteStatus.REJECTED),
	QuoteStatus.APPROVED to setOf(QuoteStatus.CONVERTED_TO_ORDER, QuoteStatus.REJECTED),
	QuoteStatus.CONVERTED_TO_ORDER to emptySet(),
	QuoteStatus.REJECTED to emptySet(),
)

@Service
class QuoteService(
	private val quoteRepository: QuoteRepository,
	private val quoteLineItemRepository: QuoteLineItemRepository,
	private val pricingLookupCache: PricingLookupCache,
	private val orderRepository: OrderRepository,
	private val strategyResolver: PricingStrategyResolver,
	private val inventoryService: InventoryService,
	private val eventPublisher: ApplicationEventPublisher,
) {

	@Transactional
	fun create(request: CreateQuoteRequest): QuoteResponse {
		val tenantId = TenantContext.getCurrentTenant()
		val quote = quoteRepository.save(
			Quote(
				tenantId = tenantId,
				quoteNumber = generateQuoteNumber(tenantId),
				customerName = request.customerName,
				customerEmail = request.customerEmail,
				createdBy = CurrentUser.getId(),
			),
		)
		return quote.toResponse(emptyList())
	}

	@Transactional(readOnly = true)
	fun list(): List<QuoteResponse> {
		val tenantId = TenantContext.getCurrentTenant()
		return quoteRepository.findAllByTenantId(tenantId).map { quote ->
			quote.toResponse(quoteLineItemRepository.findAllByTenantIdAndQuoteId(tenantId, quote.id!!))
		}
	}

	@Transactional(readOnly = true)
	fun get(quoteId: UUID): QuoteResponse {
		val tenantId = TenantContext.getCurrentTenant()
		val quote = findOwned(tenantId, quoteId)
		return quote.toResponse(quoteLineItemRepository.findAllByTenantIdAndQuoteId(tenantId, quoteId))
	}

	@Transactional
	fun addLineItem(quoteId: UUID, request: AddQuoteLineItemRequest): QuoteResponse {
		val tenantId = TenantContext.getCurrentTenant()
		val quote = findOwned(tenantId, quoteId)
		requireEditable(quote)

		val product = pricingLookupCache.findProduct(tenantId, request.productId)
			?: throw ResourceNotFoundException("Product ${request.productId} not found")

		val activeRules = pricingLookupCache.findActiveRules(tenantId, product.id!!)
		val rule = activeRules.maxByOrNull { it.priority }

		val lineTotal = strategyResolver.resolve(rule?.strategyType ?: PricingStrategyType.FIXED).calculateLineTotal(
			PricingContext(
				baseUnitPrice = product.baseUnitPrice,
				quantity = request.quantity,
				width = request.width,
				height = request.height,
				config = rule?.config ?: emptyMap(),
			),
		)
		val effectiveUnitPrice = lineTotal.divide(request.quantity, 4, RoundingMode.HALF_UP)

		quoteLineItemRepository.save(
			QuoteLineItem(
				tenantId = tenantId,
				quoteId = quoteId,
				productId = product.id!!,
				quantity = request.quantity,
				width = request.width,
				height = request.height,
				unitPrice = effectiveUnitPrice,
				lineTotal = lineTotal,
			),
		)

		return recalculateTotal(tenantId, quote)
	}

	@Transactional
	fun removeLineItem(quoteId: UUID, lineItemId: UUID): QuoteResponse {
		val tenantId = TenantContext.getCurrentTenant()
		val quote = findOwned(tenantId, quoteId)
		requireEditable(quote)

		val lineItem = quoteLineItemRepository.findByTenantIdAndQuoteIdAndId(tenantId, quoteId, lineItemId)
			?: throw ResourceNotFoundException("Line item $lineItemId not found on quote $quoteId")
		quoteLineItemRepository.delete(lineItem)

		return recalculateTotal(tenantId, quote)
	}

	@Transactional
	fun updateStatus(quoteId: UUID, newStatus: QuoteStatus): QuoteResponse {
		val tenantId = TenantContext.getCurrentTenant()
		val quote = findOwned(tenantId, quoteId)

		val allowedNextStatuses = ALLOWED_TRANSITIONS.getValue(quote.status)
		if (newStatus !in allowedNextStatuses) {
			throw InvalidQuoteStatusTransitionException(quote.status.name, newStatus.name)
		}
		if (newStatus == QuoteStatus.APPROVED) {
			val lineItemCount = quoteLineItemRepository.findAllByTenantIdAndQuoteId(tenantId, quoteId).size
			if (lineItemCount == 0) throw EmptyQuoteException(quoteId)
		}

		quote.status = newStatus
		// Flush here so the response carries the new updatedAt. The auditing listener only sets
		// it during flush, so without this we would return the value from before the update.
		val saved = quoteRepository.saveAndFlush(quote)

		if (newStatus == QuoteStatus.CONVERTED_TO_ORDER) {
			// Do this before creating the order, not after. If stock is short the whole conversion
			// has to stop, and running it in this same transaction means an order can never be
			// saved without its material having been taken out of stock.
			inventoryService.consumeForConversion(
				tenantId,
				quoteLineItemRepository.findAllByTenantIdAndQuoteId(tenantId, quoteId),
			)

			val order = orderRepository.save(
				Order(
					tenantId = tenantId,
					quoteId = quoteId,
					orderNumber = generateOrderNumber(tenantId),
					customerName = quote.customerName,
					customerEmail = quote.customerEmail,
					totalAmount = quote.totalAmount,
					createdBy = CurrentUser.getId(),
				),
			)
			eventPublisher.publishEvent(
				OrderConvertedEvent(
					orderId = order.id!!,
					quoteId = quoteId,
					tenantId = tenantId,
					orderNumber = order.orderNumber,
					customerName = order.customerName,
					customerEmail = order.customerEmail,
					totalAmount = order.totalAmount,
					createdBy = order.createdBy,
				),
			)
		}

		return saved.toResponse(quoteLineItemRepository.findAllByTenantIdAndQuoteId(tenantId, quoteId))
	}

	private fun recalculateTotal(tenantId: UUID, quote: Quote): QuoteResponse {
		val lineItems = quoteLineItemRepository.findAllByTenantIdAndQuoteId(tenantId, quote.id!!)
		quote.totalAmount = lineItems.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.lineTotal) }
		val saved = quoteRepository.saveAndFlush(quote)
		return saved.toResponse(lineItems)
	}

	private fun findOwned(tenantId: UUID, quoteId: UUID): Quote =
		quoteRepository.findByTenantIdAndId(tenantId, quoteId)
			?: throw ResourceNotFoundException("Quote $quoteId not found")

	private fun requireEditable(quote: Quote) {
		if (quote.status != QuoteStatus.DRAFT) {
			throw QuoteNotEditableException(quote.id!!, quote.status.name)
		}
	}

	private fun generateQuoteNumber(tenantId: UUID): String {
		val datePrefix = LocalDate.now().format(QUOTE_NUMBER_DATE_FORMAT)
		repeat(10) {
			val candidate = "Q-$datePrefix-${(1000..9999).random()}"
			if (!quoteRepository.existsByTenantIdAndQuoteNumber(tenantId, candidate)) return candidate
		}
		error("Failed to generate a unique quote number after 10 attempts")
	}

	private fun generateOrderNumber(tenantId: UUID): String {
		val datePrefix = LocalDate.now().format(QUOTE_NUMBER_DATE_FORMAT)
		repeat(10) {
			val candidate = "ORD-$datePrefix-${(1000..9999).random()}"
			if (!orderRepository.existsByTenantIdAndOrderNumber(tenantId, candidate)) return candidate
		}
		error("Failed to generate a unique order number after 10 attempts")
	}

	private fun Quote.toResponse(lineItems: List<QuoteLineItem>) = QuoteResponse(
		id = id!!,
		quoteNumber = quoteNumber,
		customerName = customerName,
		customerEmail = customerEmail,
		status = status,
		totalAmount = totalAmount,
		lineItems = lineItems.map {
			QuoteLineItemResponse(
				id = it.id!!,
				productId = it.productId,
				quantity = it.quantity,
				width = it.width,
				height = it.height,
				unitPrice = it.unitPrice,
				lineTotal = it.lineTotal,
			)
		},
		createdAt = createdAt,
		updatedAt = updatedAt,
	)
}
