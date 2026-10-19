package com.fiap.mechanicalhub.orchestrator.infrastructure.messaging.contracts

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Copia local dos contratos dos comandos publicados em
 * `mechanical-hub-{orders,stock,billing}-commands.fifo` (spec secao 3,
 * "Publica"). Envelope e payload no mesmo nivel do JSON; todo comando leva
 * `idempotencyKey = {transactionId}:{STEP}`.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = ReserveStockMessage::class, name = "reserveStock"),
    JsonSubTypes.Type(value = GenerateBudgetMessage::class, name = "generateBudget"),
    JsonSubTypes.Type(value = GeneratePaymentLinkMessage::class, name = "generatePaymentLink"),
    JsonSubTypes.Type(value = ConsumeStockMessage::class, name = "consumeStock"),
    JsonSubTypes.Type(value = RestoreStockMessage::class, name = "restoreStock"),
    JsonSubTypes.Type(value = SendApprovalMessage::class, name = "sendApproval"),
    JsonSubTypes.Type(value = MarkStockPendingMessage::class, name = "markStockPending"),
    JsonSubTypes.Type(value = ClearStockPendingMessage::class, name = "clearStockPending"),
    JsonSubTypes.Type(value = SendPaymentLinkMessage::class, name = "sendPaymentLink"),
    JsonSubTypes.Type(value = ReleaseOrderMessage::class, name = "releaseOrder"),
    JsonSubTypes.Type(value = CancelOrderMessage::class, name = "cancelOrder"),
)
sealed interface CommandMessage {
    val eventId: UUID
    val transactionId: UUID
    val orderId: UUID
    val type: String
    val version: Int
    val timestamp: Instant
    val idempotencyKey: String
}

data class ReserveStockMessage(
    override val eventId: UUID,
    override val transactionId: UUID,
    override val orderId: UUID,
    override val timestamp: Instant,
    override val idempotencyKey: String,
    val repairs: List<RepairReferenceDto>,
    override val version: Int = CONTRACT_VERSION,
) : CommandMessage {
    override val type = "reserveStock"
}

data class GenerateBudgetMessage(
    override val eventId: UUID,
    override val transactionId: UUID,
    override val orderId: UUID,
    override val timestamp: Instant,
    override val idempotencyKey: String,
    val repairs: List<RepairReferenceDto>,
    val reservedMaterials: List<ReservedMaterialDto>,
    override val version: Int = CONTRACT_VERSION,
) : CommandMessage {
    override val type = "generateBudget"
}

data class GeneratePaymentLinkMessage(
    override val eventId: UUID,
    override val transactionId: UUID,
    override val orderId: UUID,
    override val timestamp: Instant,
    override val idempotencyKey: String,
    val budgetId: UUID,
    val orderCost: BigDecimal,
    override val version: Int = CONTRACT_VERSION,
) : CommandMessage {
    override val type = "generatePaymentLink"
}

data class ConsumeStockMessage(
    override val eventId: UUID,
    override val transactionId: UUID,
    override val orderId: UUID,
    override val timestamp: Instant,
    override val idempotencyKey: String,
    val reservedMaterials: List<ReservedMaterialDto>,
    override val version: Int = CONTRACT_VERSION,
) : CommandMessage {
    override val type = "consumeStock"
}

data class RestoreStockMessage(
    override val eventId: UUID,
    override val transactionId: UUID,
    override val orderId: UUID,
    override val timestamp: Instant,
    override val idempotencyKey: String,
    val reservedMaterials: List<ReservedMaterialDto>,
    override val version: Int = CONTRACT_VERSION,
) : CommandMessage {
    override val type = "restoreStock"
}

data class SendApprovalMessage(
    override val eventId: UUID,
    override val transactionId: UUID,
    override val orderId: UUID,
    override val timestamp: Instant,
    override val idempotencyKey: String,
    val budgetId: UUID,
    val orderCost: BigDecimal,
    override val version: Int = CONTRACT_VERSION,
) : CommandMessage {
    override val type = "sendApproval"
}

data class MarkStockPendingMessage(
    override val eventId: UUID,
    override val transactionId: UUID,
    override val orderId: UUID,
    override val timestamp: Instant,
    override val idempotencyKey: String,
    override val version: Int = CONTRACT_VERSION,
) : CommandMessage {
    override val type = "markStockPending"
}

data class ClearStockPendingMessage(
    override val eventId: UUID,
    override val transactionId: UUID,
    override val orderId: UUID,
    override val timestamp: Instant,
    override val idempotencyKey: String,
    override val version: Int = CONTRACT_VERSION,
) : CommandMessage {
    override val type = "clearStockPending"
}

data class SendPaymentLinkMessage(
    override val eventId: UUID,
    override val transactionId: UUID,
    override val orderId: UUID,
    override val timestamp: Instant,
    override val idempotencyKey: String,
    val paymentLink: String,
    override val version: Int = CONTRACT_VERSION,
) : CommandMessage {
    override val type = "sendPaymentLink"
}

data class ReleaseOrderMessage(
    override val eventId: UUID,
    override val transactionId: UUID,
    override val orderId: UUID,
    override val timestamp: Instant,
    override val idempotencyKey: String,
    override val version: Int = CONTRACT_VERSION,
) : CommandMessage {
    override val type = "releaseOrder"
}

/** `reason = PAYMENT_NOT_CONFIRMED` (TD_paymentFailed F4). */
data class CancelOrderMessage(
    override val eventId: UUID,
    override val transactionId: UUID,
    override val orderId: UUID,
    override val timestamp: Instant,
    override val idempotencyKey: String,
    val reason: String,
    override val version: Int = CONTRACT_VERSION,
) : CommandMessage {
    override val type = "cancelOrder"
}
