package com.fiap.mechanicalhub.orchestrator.infrastructure.observability

import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStepEnum
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.MongoCollections
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

/**
 * Gauges lidos do MongoDB a cada scrape:
 * `outbox_oldest_pending_age_seconds` (idade do `PENDING` mais antigo; alerta acima de 60 s)
 * e `saga_active{current_step}` (sagas em andamento por passo, cache curto para nao
 * consultar o banco uma vez por passo).
 */
@Component
class OrchestratorGauges(
    private val mongoTemplate: MongoTemplate,
    private val clock: Clock,
    registry: MeterRegistry,
) {
    @Volatile private var cachedAt = 0L

    @Volatile private var cachedCounts: Map<String, Long> = emptyMap()

    init {
        Gauge
            .builder("outbox.oldest.pending.age", this) { it.oldestPendingAgeSeconds() }
            .baseUnit("seconds")
            .description("Idade da mensagem PENDING mais antiga do outbox_events")
            .register(registry)
        SagaStepEnum.entries.forEach { step ->
            Gauge
                .builder("saga.active", this) { it.activeSagas(step) }
                .tag("current_step", step.name)
                .description("Sagas em andamento por currentStep")
                .register(registry)
        }
    }

    fun oldestPendingAgeSeconds(): Double =
        runCatching {
            val oldest =
                mongoTemplate.findOne(
                    Query(Criteria.where("status").`is`("PENDING")).with(Sort.by(Sort.Direction.ASC, "createdAt")).limit(1),
                    Document::class.java,
                    MongoCollections.OUTBOX_EVENTS,
                )
            val createdAt = oldest?.getDate("createdAt")?.toInstant()
            if (createdAt == null) 0.0 else Duration.between(createdAt, clock.instant()).toMillis().coerceAtLeast(0) / MILLIS
        }.onFailure { log.warn("Nao foi possivel medir o outbox: {}", it.message) }
            .getOrDefault(Double.NaN)

    fun activeSagas(step: SagaStepEnum): Double =
        runCatching { counts()[step.name] ?: 0L }
            .onFailure { log.warn("Nao foi possivel contar as sagas ativas: {}", it.message) }
            .map { it.toDouble() }
            .getOrDefault(Double.NaN)

    private fun counts(): Map<String, Long> {
        val now = clock.millis()
        if (now - cachedAt < CACHE_MILLIS && cachedAt != 0L) return cachedCounts
        val result =
            mongoTemplate
                .getCollection(MongoCollections.TRANSACTION_REGISTER)
                .aggregate(
                    listOf(
                        Document("\$match", Document("currentStep", Document("\$ne", null))),
                        Document("\$group", Document("_id", "\$currentStep").append("count", Document("\$sum", 1))),
                    ),
                ).associate { it.getString("_id") to (it.get("count") as Number).toLong() }
        cachedCounts = result
        cachedAt = now
        return result
    }

    private companion object {
        const val MILLIS = 1000.0
        const val CACHE_MILLIS = 5_000L
        val log = LoggerFactory.getLogger(OrchestratorGauges::class.java)
    }
}
