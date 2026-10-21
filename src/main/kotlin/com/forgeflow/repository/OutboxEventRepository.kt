package com.forgeflow.repository

import com.forgeflow.domain.OutboxEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface OutboxEventRepository : JpaRepository<OutboxEvent, UUID> {

	/**
	 * The oldest unpublished events, locked until the calling transaction ends.
	 *
	 * `SKIP LOCKED` is what makes it safe to run more than one app instance: a second relay
	 * skips the rows the first one is already sending instead of waiting on them or sending them
	 * again. Events that have already failed [maxAttempts] times are left out, so one broken
	 * event can't be retried forever; they stay in the table to be looked at.
	 */
	@Transactional
	@Query(
		value = """
			SELECT * FROM outbox_events
			WHERE published_at IS NULL AND attempts < :maxAttempts
			ORDER BY created_at
			LIMIT :limit
			FOR UPDATE SKIP LOCKED
		""",
		nativeQuery = true,
	)
	fun lockNextPending(@Param("limit") limit: Int, @Param("maxAttempts") maxAttempts: Int): List<OutboxEvent>

	/** Events still waiting to be sent (the relay will keep trying these). */
	@Transactional(readOnly = true)
	@Query("select count(e) from OutboxEvent e where e.publishedAt is null and e.attempts < :maxAttempts")
	fun countPending(@Param("maxAttempts") maxAttempts: Int): Long

	/** Events the relay has given up on; these need a person to look at them. */
	@Transactional(readOnly = true)
	@Query("select count(e) from OutboxEvent e where e.publishedAt is null and e.attempts >= :maxAttempts")
	fun countGivenUp(@Param("maxAttempts") maxAttempts: Int): Long
}
