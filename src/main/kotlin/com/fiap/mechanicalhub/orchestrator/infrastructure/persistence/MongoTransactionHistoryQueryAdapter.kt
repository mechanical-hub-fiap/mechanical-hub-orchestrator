package com.fiap.mechanicalhub.orchestrator.infrastructure.persistence

import com.fiap.mechanicalhub.orchestrator.application.ports.TransactionHistoryEntry
import com.fiap.mechanicalhub.orchestrator.application.ports.TransactionHistoryQueryPort
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.documents.TransactionHistoryDocument
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository
import java.util.UUID

/** Leitura de `transaction_history` pelo indice `{ orderId, seq }`. */
@Repository
class MongoTransactionHistoryQueryAdapter(
    private val mongoTemplate: MongoTemplate,
) : TransactionHistoryQueryPort {
    override fun findByOrderId(orderId: UUID): List<TransactionHistoryEntry> =
        mongoTemplate
            .find(
                Query(Criteria.where("orderId").`is`(orderId.toString()))
                    .with(Sort.by(Sort.Order.asc("seq"), Sort.Order.asc("receivedAt"))),
                TransactionHistoryDocument::class.java,
            ).map { document ->
                TransactionHistoryEntry(
                    transactionId = document.transactionId?.let(UUID::fromString),
                    orderId = UUID.fromString(document.orderId),
                    seq = document.seq,
                    messageType = document.messageType,
                    messageKey = document.messageKey,
                    stepBefore = document.stepBefore,
                    stepAfter = document.stepAfter,
                    outcome = document.outcome,
                    receivedAt = document.receivedAt,
                )
            }
}
