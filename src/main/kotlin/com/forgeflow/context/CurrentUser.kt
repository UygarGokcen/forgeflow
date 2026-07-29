package com.forgeflow.context

import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

/** [JwtAuthenticationFilter][com.forgeflow.config.JwtAuthenticationFilter] puts the user id
 * from the JWT into the security context, and this reads it back out. */
object CurrentUser {

	fun getId(): UUID =
		SecurityContextHolder.getContext().authentication?.principal as? UUID
			?: error("No authenticated user in the current security context")
}
