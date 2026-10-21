package com.forgeflow.dto

import org.springframework.data.domain.Page

/**
 * One page of a list endpoint.
 *
 * Returned instead of Spring Data's [Page] itself so the JSON shape is ours and stays stable;
 * Spring Data warns that serializing `PageImpl` directly is not a stable format.
 */
data class PageResponse<T>(
	val content: List<T>,
	val page: Int,
	val size: Int,
	val totalElements: Long,
	val totalPages: Int,
) {
	companion object {
		fun <E : Any, T> of(page: Page<E>, mapper: (E) -> T) = PageResponse(
			content = page.content.map(mapper),
			page = page.number,
			size = page.size,
			totalElements = page.totalElements,
			totalPages = page.totalPages,
		)
	}
}
