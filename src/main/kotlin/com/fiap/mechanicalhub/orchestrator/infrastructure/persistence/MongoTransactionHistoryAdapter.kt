package com.fiap.mechanicalhub.orchestrator.infrastructure.persistence

import com.fiap.mechanicalhub.orchestrator.application.ports.DuplicateMessageException
import com.fiap.mechanicalhub.orchestrator.application.ports.TransactionHistoryEntry
import com.fiap.mechanicalhub.orchestrator.application.ports.TransactionHistoryPort
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.documents.TransactionHistoryDocument
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.mappers.toDocument
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository

/**
 * `transaction_history`. O indice unico em `messageKey` e a garantia final da
 * deduplicacao: `E11000` no insert vira [DuplicateMessageException] (o Mongo
 * aborta a transacao corrente nesse caso).
 */
@Repository
class MongoTransactionHistoryAdapter(
    private val mongoTemplate: MongoTemplate,
) : TransactionHistoryPort {
    override fun existsByMessageKey(messageKey: String): Boolean =
        mongoTemplate.exists(Query(Criteria.where(MESSAGE_KEY).`is`(messageKey)), TransactionHistoryDocument::class.java)

    override fun append(entry: TransactionHistoryEntry) {
        try {
            mongoTemplate.insert(entry.toDocument())
        } catch (exception: DuplicateKeyException) {
            throw DuplicateMessageException(entry.messageKey, exception)
        }
    }

    private companion object {
        const val MESSAGE_KEY = "messageKey"
    }
}
