package com.fiap.mechanicalhub.orchestrator.infrastructure.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OrchestratorProperties::class)
class PropertiesConfig
