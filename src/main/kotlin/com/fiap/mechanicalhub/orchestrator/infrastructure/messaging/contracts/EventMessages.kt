package com.fiap.mechanicalhub.orchestrator.infrastructure.messaging.contracts

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Versao dos contratos JSON desta copia local (infrastructure.md §4 e §6). */
const val CONTRACT_VERSION = 1

/**
 * Copia local dos contratos dos eventos consumidos de
 * `mechanical-hub-orchestrator-events.fifo` (spec secao 3, "Consome").
 * Envelope e payload ficam no mesmo nivel do JSON; o `type` escolhe a classe.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = DiagnosisCompletedMessage::class, name = "diagnosisCompleted"),
    JsonSubTypes.Type(value = StockPendingMessage::class, name = "stockPending"),
    JsonSubTypes.Type(value = StockReservedMessage::class, name = "stockReserved"),
    JsonSubTypes.Type(value = BudgetGeneratedMessage::class, name = "budgetGenerated"),
    JsonSubTypes.Type(value = OrderApprovedMessage::class, name = "orderApproved"),
    JsonSubTypes.Type(value = OrderRejectedMessage::class, name = "orderRejected"),
    JsonSubTypes.Type(value = PaymentLinkGeneratedMessage::class, name = "paymentLinkGenerated"),
    JsonSubTypes.Type(value = PaymentConfirmedMessage::class, name = "paymentConfirmed"),
    JsonSubTypes.Type(value = PaymentFailedMessage::class, name = "paymentFailed"),
    JsonSubTypes.Type(value = OrderCanceledMessage::class, name = "orderCanceled"),
    JsonSubTypes.Type(value = OrderFinishedMessage::class, name = "orderFinished"),
    JsonSubTypes.Type(value = StockConsumedMessage::class, name = "stockConsumed"),
    JsonSubTypes.Type(value = StockRestoredMessage::class, name = "stockRestored"),
)
sealed interface EventMessage {
    val eventId: UUID
    val transactionId: UUID?
    val orderId: UUID
    val type: String
    val version: Int
    val timestamp: Instant
}

data class RepairReferenceDto(
    val repairId: UUID,
)

data class ReservedMaterialDto(
    val materialId: UUID,
    val quantity: Int,
)

/** Publicado pelo `orders`; ainda nao ha `transactionId` (a saga nasce deste evento). */
data class DiagnosisCompletedMessage(
    override val eventId: UUID,
    override val orderId: UUID,
    override val version: Int,
    override val timestamp: Instant,
    val repairs: List<RepairReferenceDto>,
    override val transactionId: UUID? = null,
) : EventMessage {
    override val type = "diagnosisCompleted"
}

data class StockPendingMessage(
    override val eventId: UUID,
    override val transactionId: UUID,
    override val orderId: UUID,
    override val version: Int,
    override val timestamp: Instant,
) : EventMessage {
    override val type = "stockPending"
}

data class StockReservedMessage(
    override val eventId: UUID,
    override val transactionId: UUID,
    override val orderId: UUID,
    override val version: Int,
    override val timestamp: Instant,
    val reservedMaterials: List<ReservedMaterialDto>,
) : EventMessage {
    override val type = "stockReserved"
}

data class BudgetGeneratedMessage(
    override val eventId: UUID,
    override val transactionId: UUID,
    override val orderId: UUID,
    override val version: Int,
    override val timestamp: Instant,
    val budgetId: UUID,
    val orderCost: BigDecimal,
) : EventMessage {
    override val type = "budgetGenerated"
}

data class OrderApprovedMessage(
    override val eventId: UUID,
    override val transactionId: UUID,
    override val orderId: UUID,
    override val version: Int,
    override val timestamp: Instant,
) : EventMessage {
    override val type = "orderApproved"
}

data class OrderRejectedMessage(
    override val eventId: UUID,
    override val transactionId: UUID,
    override val orderId: UUID,
    override val version: Int,
    override val timestamp: Instant,
) : EventMessage {
    override val type = "orderRejected"
}

data class PaymentLinkGeneratedMessage(
    override val eventId: UUID,
    override val transactionId: UUID,
    override val orderId: UUID,
    override val version: Int,
    override val timestamp: Instant,
    val paymentLink: String,
    val expiresAt: Instant,
) : EventMessage {
    override val type = "paymentLinkGenerated"
}

data class PaymentConfirmedMessage(
    override val eventId: UUID,
    override val transactionId: UUID,
    override val orderId: UUID,
    override val version: Int,
    override val timestamp: Instant,
    val paymentId: String,
) : EventMessage {
    override val type = "paymentConfirmed"
}

/** `reason`: `EXPIRED` ou `FAILED` (TD_paymentFailed F3). */
data class PaymentFailedMessage(
    override val eventId: UUID,
    override val transactionId: UUID,
    override val orderId: UUID,
    override val version: Int,
    override val timestamp: Instant,
    val reason: String,
) : EventMessage {
    override val type = "paymentFailed"
}

data class OrderCanceledMessage(
    override val eventId: UUID,
    override val transactionId: UUID,
    override val orderId: UUID,
    override val version: Int,
    override val timestamp: Instant,
) : EventMessage {
    override val type = "orderCanceled"
}

data class OrderFinishedMessage(
    override val eventId: UUID,
    override val transactionId: UUID,
    override val orderId: UUID,
    override val version: Int,
    override val timestamp: Instant,
) : EventMessage {
    override val type = "orderFinished"
}

data class StockConsumedMessage(
    override val eventId: UUID,
    override val transactionId: UUID,
    override val orderId: UUID,
    override val version: Int,
    override val timestamp: Instant,
) : EventMessage {
    override val type = "stockConsumed"
}

data class StockRestoredMessage(
    override val eventId: UUID,
    override val transactionId: UUID,
    override val orderId: UUID,
    override val version: Int,
    override val timestamp: Instant,
) : EventMessage {
    override val type = "stockRestored"
}
