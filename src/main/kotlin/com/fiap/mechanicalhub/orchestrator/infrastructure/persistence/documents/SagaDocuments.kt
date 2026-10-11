package com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.documents

import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStatusEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStepEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.TransactionOutcomeEnum
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.MongoCollections
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import java.math.BigDecimal
import java.time.Instant

/**
 * Modelos de persistencia (data_model.md §5). Os nomes dos campos sao os dos
 * indices criados pelo `MongoIndexInitializer`; UUIDs gravados como string.
 */
@Document(MongoCollections.TRANSACTION_REGISTER)
data class TransactionRegisterDocument(
    @Id val id: String,
    val orderId: String,
    val status: SagaStatusEnum,
    val currentStep: SagaStepEnum?,
    val seq: Long,
    val repairs: List<RepairDocument>,
    val reservedMaterials: List<ReservedMaterialDocument>,
    val budget: BudgetDocument?,
    val payment: PaymentDocument?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val finishedAt: Instant?,
)

data class RepairDocument(
    val repairId: String,
)

data class ReservedMaterialDocument(
    val materialId: String,
    val quantity: Int,
)

data class BudgetDocument(
    val budgetId: String,
    @Field(targetType = FieldType.DECIMAL128) val orderCost: BigDecimal,
)

data class PaymentDocument(
    val link: String?,
    val expiresAt: Instant?,
    val paymentId: String?,
)

/** Linha append-only da trilha da saga; `messageKey` tem indice unico (deduplicacao). */
@Document(MongoCollections.TRANSACTION_HISTORY)
data class TransactionHistoryDocument(
    @Id val id: ObjectId? = null,
    val transactionId: String?,
    val orderId: String,
    val seq: Long?,
    val messageType: String,
    val messageKey: String,
    val stepBefore: SagaStepEnum?,
    val stepAfter: SagaStepEnum?,
    val outcome: TransactionOutcomeEnum,
    val receivedAt: Instant,
)

enum class OutboxStatusEnum {
    PENDING,
    SENT,
}

/** Comando a publicar; `_id = eventId` e `payload` e o JSON do contrato (corpo da mensagem SQS). */
@Document(MongoCollections.OUTBOX_EVENTS)
data class OutboxEventDocument(
    @Id val id: String,
    val orderId: String,
    val seq: Long,
    val eventType: String,
    val payload: String,
    val status: OutboxStatusEnum,
    val attempts: Int,
    val lastError: String?,
    val createdAt: Instant,
    val sentAt: Instant?,
)
