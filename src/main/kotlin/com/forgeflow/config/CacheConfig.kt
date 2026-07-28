package com.forgeflow.config

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import java.time.Duration

/**
 * Redis-backed cache for hot, rarely-changing lookups on the quote pricing path (see
 * [com.forgeflow.service.PricingLookupCache]) — not a general-purpose cache for everything.
 * A short TTL is the safety net; mutations evict explicitly (write-through) so a product price
 * change or pricing rule edit doesn't sit stale for the full TTL window.
 */
@Configuration
@EnableCaching
class CacheConfig {

	@Bean
	fun cacheManagerBuilderCustomizer(): RedisCacheManagerBuilderCustomizer =
		RedisCacheManagerBuilderCustomizer { builder ->
			val config = RedisCacheConfiguration.defaultCacheConfig()
				.entryTtl(Duration.ofMinutes(5))
				.disableCachingNullValues()
				.serializeValuesWith(
					RedisSerializationContext.SerializationPair.fromSerializer(
						GenericJackson2JsonRedisSerializer(redisObjectMapper()),
					),
				)
			builder.cacheDefaults(config)
		}

	// GenericJackson2JsonRedisSerializer's no-arg constructor doesn't pick up JavaTimeModule, so
	// caching anything with an Instant field (every entity, via AuditableEntity) fails at
	// serialization time. Registering it explicitly here is what's needed for correct handling of
	// java.time types; Kotlin data class support comes along via KotlinModule. Default typing
	// (embedding "@class" in the cached JSON) is required too: cached values like
	// List<PricingRule> lose their element type to generic erasure, and without it Jackson
	// deserializes back to LinkedHashMap instead of the real entity.
	private fun redisObjectMapper(): ObjectMapper {
		val mapper = ObjectMapper()
			.registerModule(JavaTimeModule())
			.registerModule(KotlinModule.Builder().build())
		mapper.activateDefaultTyping(
			mapper.polymorphicTypeValidator,
			ObjectMapper.DefaultTyping.NON_FINAL,
			JsonTypeInfo.As.PROPERTY,
		)
		return mapper
	}
}
