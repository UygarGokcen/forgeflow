package com.forgeflow.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/** Turns on `@Scheduled`, which the outbox relay uses to poll for pending events. */
@Configuration
@EnableScheduling
class SchedulingConfig
