package com.forgeflow.config

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

const val ORDER_EVENTS_TOPIC = "forgeflow.order-events"

@Configuration
class KafkaConfig {

	@Bean
	fun orderEventsTopic(): NewTopic = TopicBuilder.name(ORDER_EVENTS_TOPIC)
		.partitions(3)
		.replicas(1)
		.build()
}
