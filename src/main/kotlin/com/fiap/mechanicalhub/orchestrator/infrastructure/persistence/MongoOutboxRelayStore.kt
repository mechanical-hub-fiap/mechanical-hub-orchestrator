package com.fiap.mechanicalhub.orchestrator.infrastructure.persistence

import com.fiap.mechanicalhub.orchestrator.application.ports.OutboxMessage
import com.fiap.mechanicalhub.orchestrator.application.ports.OutboxRelayStore
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.documents.OutboxEventDocument
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.documents.OutboxStatusEnum
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class MongoOutboxRelayStore(
    private val mongoTemplate: MongoTemplate,
) : OutboxRelayStore {
    override fun findPending(limit: Int): List<OutboxMessage> =
        mongoTemplate
            .find(
                Query(Criteria.where("status").`is`(OutboxStatusEnum.PENDING))
                    .with(Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("seq")))
                    .limit(limit),
                OutboxEventDocument::class.java,
            ).map {
                OutboxMessage(it.id, it.orderId, it.seq, it.eventType, it.payload, it.attempts, it.createdAt)
            }

    override fun markSent(
        eventId: String,
        sentAt: Instant,
    ) {
        mongoTemplate.updateFirst(
            byId(eventId),
            Update().set("status", OutboxStatusEnum.SENT).set("sentAt", sentAt),
            OutboxEventDocument::class.java,
        )
    }

    override fun markFailed(
        eventId: String,
        error: String,
    ) {
        mongoTemplate.updateFirst(
            byId(eventId),
            Update().inc("attempts", 1).set("lastError", error),
            OutboxEventDocument::class.java,
        )
    }

    private fun byId(eventId: String) = Query(Criteria.where("_id").`is`(eventId))
}
