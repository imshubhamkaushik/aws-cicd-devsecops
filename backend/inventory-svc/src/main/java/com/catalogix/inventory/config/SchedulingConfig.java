package com.catalogix.inventory.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Switches on @Scheduled (used by OperationPurgeJob). Without this annotation @Scheduled methods never run. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
