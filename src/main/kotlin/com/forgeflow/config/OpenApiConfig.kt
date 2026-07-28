package com.forgeflow.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

private const val BEARER_SECURITY_SCHEME = "bearerAuth"

@Configuration
class OpenApiConfig {

	@Bean
	fun forgeflowOpenApi(): OpenAPI = OpenAPI()
		.info(
			Info()
				.title("ForgeFlow API")
				.description(
					"Multi-tenant manufacturing CPQ & order management platform. " +
						"Register a tenant, log in to get a JWT, then click Authorize below and " +
						"paste the token (without the 'Bearer ' prefix) to call protected endpoints.",
				)
				.version("v1"),
		)
		.addSecurityItem(SecurityRequirement().addList(BEARER_SECURITY_SCHEME))
		.components(
			Components().addSecuritySchemes(
				BEARER_SECURITY_SCHEME,
				SecurityScheme()
					.name(BEARER_SECURITY_SCHEME)
					.type(SecurityScheme.Type.HTTP)
					.scheme("bearer")
					.bearerFormat("JWT"),
			),
		)
}
