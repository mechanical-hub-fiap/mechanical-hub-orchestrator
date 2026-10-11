package com.fiap.mechanicalhub.orchestrator.infrastructure.persistence

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.stereotype.Component

/**
 * Cria explicitamente as colecoes e os indices da secao 4 da spec durante a subida.
 *
 * Roda no `afterPropertiesSet`, ou seja, durante o refresh do contexto: qualquer
 * falha (Mongo fora do ar, indice conflitante com um ja existente) interrompe a
 * subida da aplicacao antes de o consumidor SQS e o relay comecarem a trabalhar.
 */
@Component
class MongoIndexInitializer(
    private val mongoTemplate: MongoTemplate,
) : InitializingBean {
    override fun afterPropertiesSet() {
        COLLECTIONS.forEach(::createCollectionIfMissing)
        INDEXES.forEach { (collection, indexes) ->
            val indexOperations = mongoTemplate.indexOps(collection)
            indexes.forEach { index -> indexOperations.createIndex(index) }
        }
        log.info("Colecoes e indices do MongoDB verificados: {}", INDEXES.mapValues { (_, indexes) -> indexes.size })
    }

    private fun createCollectionIfMissing(collection: String) {
        if (!mongoTemplate.collectionExists(collection)) {
            mongoTemplate.createCollection(collection)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MongoIndexInitializer::class.java)

        val COLLECTIONS: List<String> =
            listOf(
                MongoCollections.TRANSACTION_REGISTER,
                MongoCollections.TRANSACTION_HISTORY,
                MongoCollections.OUTBOX_EVENTS,
                MongoCollections.SHEDLOCK,
            )

        val INDEXES: Map<String, List<Index>> =
            mapOf(
                MongoCollections.TRANSACTION_REGISTER to
                    listOf(
                        ascending("orderId").unique().named("ux_transaction_register_order_id"),
                        ascending("status", "currentStep", "updatedAt")
                            .named("ix_transaction_register_status_step_updated_at"),
                    ),
                MongoCollections.TRANSACTION_HISTORY to
                    listOf(
                        ascending("messageKey").unique().named("ux_transaction_history_message_key"),
                        ascending("orderId", "seq").named("ix_transaction_history_order_id_seq"),
                        ascending("messageType", "receivedAt").named("ix_transaction_history_message_type_received_at"),
                    ),
                MongoCollections.OUTBOX_EVENTS to
                    listOf(
                        ascending("status", "createdAt").named("ix_outbox_events_status_created_at"),
                        ascending("orderId", "seq").unique().named("ux_outbox_events_order_id_seq"),
                    ),
            )

        private fun ascending(vararg fields: String): Index =
            fields.fold(Index()) { index, field -> index.on(field, Sort.Direction.ASC) }
    }
}
