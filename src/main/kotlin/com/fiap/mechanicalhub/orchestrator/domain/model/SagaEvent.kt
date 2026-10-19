package com.fiap.mechanicalhub.orchestrator.domain.model

import com.fiap.mechanicalhub.orchestrator.domain.enums.PaymentFailureReasonEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaEventTypeEnum
import java.time.Instant
import java.util.UUID

/** Evento consumido pela saga, ja convertido do contrato JSON. */
sealed interface SagaEvent {
    val type: SagaEventTypeEnum
    val orderId: UUID
}

/** Abre a saga; ainda nao existe `transactionId`. */
data class DiagnosisCompleted(
    override val orderId: UUID,
    val repairs: List<RepairReference>,
) : SagaEvent {
    override val type = SagaEventTypeEnum.DIAGNOSIS_COMPLETED
}

/** Resposta de um passo de uma saga existente, sempre aplicada com a guarda de passo. */
sealed interface SagaResponseEvent : SagaEvent {
    val transactionId: UUID
}

data class StockPending(
    override val transactionId: UUID,
    override val orderId: UUID,
) : SagaResponseEvent {
    override val type = SagaEventTypeEnum.STOCK_PENDING
}

data class StockReserved(
    override val transactionId: UUID,
    override val orderId: UUID,
    val reservedMaterials: List<ReservedMaterial>,
) : SagaResponseEvent {
    override val type = SagaEventTypeEnum.STOCK_RESERVED
}

data class BudgetGenerated(
    override val transactionId: UUID,
    override val orderId: UUID,
    val budget: Budget,
) : SagaResponseEvent {
    override val type = SagaEventTypeEnum.BUDGET_GENERATED
}

data class OrderApproved(
    override val transactionId: UUID,
    override val orderId: UUID,
) : SagaResponseEvent {
    override val type = SagaEventTypeEnum.ORDER_APPROVED
}

data class OrderRejected(
    override val transactionId: UUID,
    override val orderId: UUID,
) : SagaResponseEvent {
    override val type = SagaEventTypeEnum.ORDER_REJECTED
}

data class PaymentLinkGenerated(
    override val transactionId: UUID,
    override val orderId: UUID,
    val paymentLink: String,
    val expiresAt: Instant,
) : SagaResponseEvent {
    override val type = SagaEventTypeEnum.PAYMENT_LINK_GENERATED
}

data class PaymentConfirmed(
    override val transactionId: UUID,
    override val orderId: UUID,
    val paymentId: String,
) : SagaResponseEvent {
    override val type = SagaEventTypeEnum.PAYMENT_CONFIRMED
}

data class PaymentFailed(
    override val transactionId: UUID,
    override val orderId: UUID,
    val reason: PaymentFailureReasonEnum,
) : SagaResponseEvent {
    override val type = SagaEventTypeEnum.PAYMENT_FAILED
}

data class OrderCanceled(
    override val transactionId: UUID,
    override val orderId: UUID,
) : SagaResponseEvent {
    override val type = SagaEventTypeEnum.ORDER_CANCELED
}

data class OrderFinished(
    override val transactionId: UUID,
    override val orderId: UUID,
) : SagaResponseEvent {
    override val type = SagaEventTypeEnum.ORDER_FINISHED
}

data class StockConsumed(
    override val transactionId: UUID,
    override val orderId: UUID,
) : SagaResponseEvent {
    override val type = SagaEventTypeEnum.STOCK_CONSUMED
}

data class StockRestored(
    override val transactionId: UUID,
    override val orderId: UUID,
) : SagaResponseEvent {
    override val type = SagaEventTypeEnum.STOCK_RESTORED
}
