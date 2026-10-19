package com.fiap.mechanicalhub.orchestrator.infrastructure.config

import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.MongoCollections
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.mongo.MongoLockProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.MongoTemplate

/**
 * Lease de lideranca do relay da outbox (ShedLock, provider Mongo) na colecao `shedLock`.
 * A colecao e criada na subida pelo `MongoIndexInitializer`.
 */
@Configuration(proxyBeanMethods = false)
class ShedLockConfig {
    @Bean
    fun lockProvider(mongoTemplate: MongoTemplate): LockProvider =
        MongoLockProvider(mongoTemplate.getCollection(MongoCollections.SHEDLOCK))
}
