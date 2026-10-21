package com.forgeflow.config

import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableMethodSecurity
class SecurityConfig(
	private val jwtAuthenticationFilter: JwtAuthenticationFilter,
) {

	@Bean
	fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

	@Bean
	fun filterChain(http: HttpSecurity): SecurityFilterChain {
		http
			.csrf { it.disable() }
			.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
			.httpBasic { it.disable() }
			.formLogin { it.disable() }
			.authorizeHttpRequests {
				it.requestMatchers(
					"/api/v1/auth/**",
					// Actuator is only served on the management port (8081), never on the public
					// API port, and that port isn't published outside the host. See application.yml.
					"/actuator/**",
					"/v3/api-docs/**",
					"/swagger-ui/**",
					"/swagger-ui.html",
				).permitAll()
				it.anyRequest().authenticated()
			}
			.exceptionHandling {
				it.authenticationEntryPoint { _, response, _ ->
					response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")
				}
				it.accessDeniedHandler { _, response, _ ->
					response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden")
				}
			}
			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

		return http.build()
	}
}
