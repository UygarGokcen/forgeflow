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
 * Redis cache for the lookups on the quote pricing path (see
 * [com.forgeflow.service.PricingLookupCache]). It is not meant as a general cache for everything.
 *
 * The short TTL is only a fallback. Updates clear the cache directly, so a price change or a
 * pricing rule edit takes effect immediately instead of after the TTL.
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

	// Two things this mapper needs that the default one doesn't have:
	//
	// 1. JavaTimeModule. Without it, caching anything with an Instant field fails, and every
	//    entity has one through AuditableEntity.
	// 2. Default typing, which writes a "@class" field into the cached JSON. Generics are erased at
	//    runtime, so a cached List<PricingRule> would otherwise come back as a list of
	//    LinkedHashMap and blow up with a ClassCastException when the pricing code uses it.
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
