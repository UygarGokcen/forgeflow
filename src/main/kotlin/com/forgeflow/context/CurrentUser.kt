package com.forgeflow.context

import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

/** [JwtAuthenticationFilter][com.forgeflow.config.JwtAuthenticationFilter] sets the JWT's user id as the authentication principal. */
object CurrentUser {

	fun getId(): UUID =
		SecurityContextHolder.getContext().authentication?.principal as? UUID
			?: error("No authenticated user in the current security context")
}
