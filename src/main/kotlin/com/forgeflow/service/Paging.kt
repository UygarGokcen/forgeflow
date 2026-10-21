package com.forgeflow.service

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

/** `id` breaks ties between rows created in the same instant, so pages don't overlap or skip. */
private val NEWEST_FIRST: Sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))

/** List endpoints show the newest records first unless the caller asked for another order. */
fun Pageable.newestFirstIfUnsorted(): Pageable =
	if (sort.isSorted) this else PageRequest.of(pageNumber, pageSize, NEWEST_FIRST)
