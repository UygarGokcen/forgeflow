package com.forgeflow.service

import com.forgeflow.domain.Order
import com.forgeflow.domain.PricingRule
import com.forgeflow.domain.PricingStrategyType
import com.forgeflow.domain.Product
import com.forgeflow.domain.Quote
import com.forgeflow.domain.QuoteLineItem
import com.forgeflow.domain.QuoteStatus
import com.forgeflow.domain.UnitOfMeasure
import com.forgeflow.dto.AddQuoteLineItemRequest
import com.forgeflow.exception.EmptyQuoteException
import com.forgeflow.exception.InvalidQuoteStatusTransitionException
import com.forgeflow.exception.QuoteNotEditableException
import com.forgeflow.repository.OrderRepository
import com.forgeflow.repository.PricingRuleRepository
import com.forgeflow.repository.ProductRepository
import com.forgeflow.repository.QuoteLineItemRepository
import com.forgeflow.repository.QuoteRepository
import com.forgeflow.service.pricing.AreaBasedPricingStrategy
import com.forgeflow.service.pricing.FixedPricingStrategy
import com.forgeflow.service.pricing.PricingStrategyResolver
import com.forgeflow.service.pricing.VolumeDiscountStrategy
import com.forgeflow.support.TenantContextTestSupport
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal
import java.util.UUID

class QuoteServiceTest {

	private val quoteRepository: QuoteRepository = mock()
	private val quoteLineItemRepository: QuoteLineItemRepository = mock()
	private val productRepository: ProductRepository = mock()
	private val pricingRuleRepository: PricingRuleRepository = mock()
	private val orderRepository: OrderRepository = mock()
	private val eventPublisher: ApplicationEventPublisher = mock()
	private val strategyResolver = PricingStrategyResolver(
		listOf(FixedPricingStrategy(), VolumeDiscountStrategy(), AreaBasedPricingStrategy()),
	)
	// Real instance (no Spring context here, so @Cacheable is inert) — just delegates to the
	// mocked repositories below, same as calling them directly would.
	private val pricingLookupCache = PricingLookupCache(productRepository, pricingRuleRepository)

	private val inventoryService: InventoryService = mock()

	private val quoteService = QuoteService(
		quoteRepository,
		quoteLineItemRepository,
		pricingLookupCache,
		orderRepository,
		strategyResolver,
		inventoryService,
		eventPublisher,
	)

	private val tenantId: UUID = UUID.randomUUID()
	private val product = Product(
		tenantId = tenantId,
		sku = "PANEL-01",
		name = "Panel",
		baseUnitPrice = BigDecimal("20.00"),
		unitOfMeasure = UnitOfMeasure.SQUARE_METER,
		id = UUID.randomUUID(),
	)

	@BeforeEach
	fun bindRequestScope() = TenantContextTestSupport.bind(tenantId)

	@AfterEach
	fun unbindRequestScope() = TenantContextTestSupport.unbind()

	@Test
	fun `addLineItem prices via the product's active area-based rule`() {
		val quote = draftQuote()
		val rule = PricingRule(
			tenantId = tenantId,
			productId = product.id!!,
			strategyType = PricingStrategyType.AREA_BASED,
			config = mutableMapOf("multiplier" to 1.0),
			id = UUID.randomUUID(),
		)
		whenever(quoteRepository.findByTenantIdAndId(tenantId, quote.id!!)).thenReturn(quote)
		whenever(productRepository.findByTenantIdAndId(tenantId, product.id!!)).thenReturn(product)
		whenever(pricingRuleRepository.findAllByTenantIdAndProductIdAndIsActiveTrue(tenantId, product.id!!))
			.thenReturn(listOf(rule))
		stubLineItemPersistence(quote.id!!)
		doAnswer { it.arguments[0] as Quote }.whenever(quoteRepository).saveAndFlush(any())

		val response = quoteService.addLineItem(
			quote.id!!,
			AddQuoteLineItemRequest(product.id!!, BigDecimal("1"), BigDecimal("2.0"), BigDecimal("1.5")),
		)

		// 20.00 * 2.0 * 1.5 * 1.0 = 60.00
		assertEquals(BigDecimal("60.0000"), response.totalAmount)
	}

	@Test
	fun `addLineItem falls back to fixed pricing when the product has no active rule`() {
		val quote = draftQuote()
		whenever(quoteRepository.findByTenantIdAndId(tenantId, quote.id!!)).thenReturn(quote)
		whenever(productRepository.findByTenantIdAndId(tenantId, product.id!!)).thenReturn(product)
		whenever(pricingRuleRepository.findAllByTenantIdAndProductIdAndIsActiveTrue(tenantId, product.id!!))
			.thenReturn(emptyList())
		stubLineItemPersistence(quote.id!!)
		doAnswer { it.arguments[0] as Quote }.whenever(quoteRepository).saveAndFlush(any())

		val response = quoteService.addLineItem(
			quote.id!!,
			AddQuoteLineItemRequest(product.id!!, BigDecimal("3"), null, null),
		)

		// 20.00 * 3 = 60.00
		assertEquals(BigDecimal("60.0000"), response.totalAmount)
	}

	/** Makes save() and findAll() on the line item repository work like a small in-memory table. */
	private fun stubLineItemPersistence(quoteId: UUID) {
		val savedLineItems = mutableListOf<QuoteLineItem>()
		doAnswer {
			(it.arguments[0] as QuoteLineItem)
				.also { item -> item.id = UUID.randomUUID() }
				.also { item -> savedLineItems += item }
		}.whenever(quoteLineItemRepository).save(any())
		whenever(quoteLineItemRepository.findAllByTenantIdAndQuoteId(tenantId, quoteId))
			.thenAnswer { savedLineItems.toList() }
	}

	@Test
	fun `addLineItem rejects a non-draft quote`() {
		val quote = draftQuote().also { it.status = QuoteStatus.APPROVED }
		whenever(quoteRepository.findByTenantIdAndId(tenantId, quote.id!!)).thenReturn(quote)

		assertThrows(QuoteNotEditableException::class.java) {
			quoteService.addLineItem(
				quote.id!!,
				AddQuoteLineItemRequest(product.id!!, BigDecimal.ONE, null, null),
			)
		}
	}

	@Test
	fun `updateStatus rejects an illegal transition`() {
		val quote = draftQuote()
		whenever(quoteRepository.findByTenantIdAndId(tenantId, quote.id!!)).thenReturn(quote)

		assertThrows(InvalidQuoteStatusTransitionException::class.java) {
			quoteService.updateStatus(quote.id!!, QuoteStatus.CONVERTED_TO_ORDER)
		}
	}

	@Test
	fun `updateStatus rejects approving an empty quote`() {
		val quote = draftQuote()
		whenever(quoteRepository.findByTenantIdAndId(tenantId, quote.id!!)).thenReturn(quote)
		whenever(quoteLineItemRepository.findAllByTenantIdAndQuoteId(tenantId, quote.id!!)).thenReturn(emptyList())

		assertThrows(EmptyQuoteException::class.java) {
			quoteService.updateStatus(quote.id!!, QuoteStatus.APPROVED)
		}
	}

	@Test
	fun `updateStatus allows DRAFT to APPROVED once a line item exists`() {
		val quote = draftQuote()
		val lineItem = QuoteLineItem(
			tenantId = tenantId,
			quoteId = quote.id!!,
			productId = product.id!!,
			quantity = BigDecimal.ONE,
			unitPrice = BigDecimal("20.00"),
			lineTotal = BigDecimal("20.00"),
			id = UUID.randomUUID(),
		)
		whenever(quoteRepository.findByTenantIdAndId(tenantId, quote.id!!)).thenReturn(quote)
		whenever(quoteLineItemRepository.findAllByTenantIdAndQuoteId(tenantId, quote.id!!)).thenReturn(listOf(lineItem))
		doAnswer { it.arguments[0] as Quote }.whenever(quoteRepository).saveAndFlush(any())

		val response = quoteService.updateStatus(quote.id!!, QuoteStatus.APPROVED)

		assertEquals(QuoteStatus.APPROVED, response.status)
	}

	@Test
	fun `updateStatus creates an order when a quote converts`() {
		val quote = draftQuote().also {
			it.status = QuoteStatus.APPROVED
			it.totalAmount = BigDecimal("60.0000")
		}
		whenever(quoteRepository.findByTenantIdAndId(tenantId, quote.id!!)).thenReturn(quote)
		doAnswer { it.arguments[0] as Quote }.whenever(quoteRepository).saveAndFlush(any())
		whenever(quoteLineItemRepository.findAllByTenantIdAndQuoteId(tenantId, quote.id!!)).thenReturn(emptyList())
		whenever(orderRepository.existsByTenantIdAndOrderNumber(any(), any())).thenReturn(false)
		doAnswer { (it.arguments[0] as Order).also { order -> order.id = UUID.randomUUID() } }
			.whenever(orderRepository).save(any())

		quoteService.updateStatus(quote.id!!, QuoteStatus.CONVERTED_TO_ORDER)

		val captor = argumentCaptor<Order>()
		verify(orderRepository).save(captor.capture())
		assertEquals(quote.id, captor.firstValue.quoteId)
		assertEquals(quote.totalAmount, captor.firstValue.totalAmount)

		val eventCaptor = argumentCaptor<com.forgeflow.event.OrderConvertedEvent>()
		verify(eventPublisher).publishEvent(eventCaptor.capture())
		assertEquals(quote.id, eventCaptor.firstValue.quoteId)
		assertEquals(quote.totalAmount, eventCaptor.firstValue.totalAmount)
	}

	@Test
	fun `updateStatus aborts the conversion when material stock is short`() {
		val quote = draftQuote().also { it.status = QuoteStatus.APPROVED }
		whenever(quoteRepository.findByTenantIdAndId(tenantId, quote.id!!)).thenReturn(quote)
		doAnswer { it.arguments[0] as Quote }.whenever(quoteRepository).saveAndFlush(any())
		whenever(quoteLineItemRepository.findAllByTenantIdAndQuoteId(tenantId, quote.id!!)).thenReturn(emptyList())
		whenever(inventoryService.consumeForConversion(any(), any(), any()))
			.thenThrow(com.forgeflow.exception.InsufficientStockException(listOf("STEEL-01 is short")))

		assertThrows(com.forgeflow.exception.InsufficientStockException::class.java) {
			quoteService.updateStatus(quote.id!!, QuoteStatus.CONVERTED_TO_ORDER)
		}

		// No order, and nothing announced downstream, for a conversion that didn't happen.
		verify(orderRepository, org.mockito.kotlin.never()).save(any())
		verify(eventPublisher, org.mockito.kotlin.never()).publishEvent(any())
	}

	@Test
	fun `updateStatus does not create an order for a plain rejection`() {
		val quote = draftQuote()
		whenever(quoteRepository.findByTenantIdAndId(tenantId, quote.id!!)).thenReturn(quote)
		doAnswer { it.arguments[0] as Quote }.whenever(quoteRepository).saveAndFlush(any())
		whenever(quoteLineItemRepository.findAllByTenantIdAndQuoteId(tenantId, quote.id!!)).thenReturn(emptyList())

		quoteService.updateStatus(quote.id!!, QuoteStatus.REJECTED)

		verify(orderRepository, org.mockito.kotlin.never()).save(any())
		verify(eventPublisher, org.mockito.kotlin.never()).publishEvent(any())
	}

	private fun draftQuote() = Quote(
		tenantId = tenantId,
		quoteNumber = "Q-TEST-0001",
		customerName = "Contoso",
		createdBy = UUID.randomUUID(),
		id = UUID.randomUUID(),
	)
}
