package com.fiap.mechanicalhub.orchestrator.domain.model

import com.fiap.mechanicalhub.orchestrator.domain.enums.CancelOrderReasonEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaCommandTypeEnum
import java.math.BigDecimal
import java.util.UUID

/**
 * Comando emitido pela saga, gravado no outbox com o `seq` reservado na saga
 * (ordem de publicacao por `orderId`).
 */
sealed interface SagaCommand {
    val type: SagaCommandTypeEnum
    val transactionId: UUID
    val orderId: UUID
    val seq: Long

    val idempotencyKey: String get() = "$transactionId:${type.step}"
}

data class ReserveStockCommand(
    override val transactionId: UUID,
    override val orderId: UUID,
    override val seq: Long,
    val repairs: List<RepairReference>,
) : SagaCommand {
    override val type = SagaCommandTypeEnum.RESERVE_STOCK
}

data class GenerateBudgetCommand(
    override val transactionId: UUID,
    override val orderId: UUID,
    override val seq: Long,
    val repairs: List<RepairReference>,
    val reservedMaterials: List<ReservedMaterial>,
) : SagaCommand {
    override val type = SagaCommandTypeEnum.GENERATE_BUDGET
}

data class GeneratePaymentLinkCommand(
    override val transactionId: UUID,
    override val orderId: UUID,
    override val seq: Long,
    val budgetId: UUID,
    val orderCost: BigDecimal,
) : SagaCommand {
    override val type = SagaCommandTypeEnum.GENERATE_PAYMENT_LINK
}

data class ConsumeStockCommand(
    override val transactionId: UUID,
    override val orderId: UUID,
    override val seq: Long,
    val reservedMaterials: List<ReservedMaterial>,
) : SagaCommand {
    override val type = SagaCommandTypeEnum.CONSUME_STOCK
}

data class RestoreStockCommand(
    override val transactionId: UUID,
    override val orderId: UUID,
    override val seq: Long,
    val reservedMaterials: List<ReservedMaterial>,
) : SagaCommand {
    override val type = SagaCommandTypeEnum.RESTORE_STOCK
}

data class SendApprovalCommand(
    override val transactionId: UUID,
    override val orderId: UUID,
    override val seq: Long,
    val budgetId: UUID,
    val orderCost: BigDecimal,
) : SagaCommand {
    override val type = SagaCommandTypeEnum.SEND_APPROVAL
}

data class MarkStockPendingCommand(
    override val transactionId: UUID,
    override val orderId: UUID,
    override val seq: Long,
) : SagaCommand {
    override val type = SagaCommandTypeEnum.MARK_STOCK_PENDING
}

data class ClearStockPendingCommand(
    override val transactionId: UUID,
    override val orderId: UUID,
    override val seq: Long,
) : SagaCommand {
    override val type = SagaCommandTypeEnum.CLEAR_STOCK_PENDING
}

data class SendPaymentLinkCommand(
    override val transactionId: UUID,
    override val orderId: UUID,
    override val seq: Long,
    val paymentLink: String,
) : SagaCommand {
    override val type = SagaCommandTypeEnum.SEND_PAYMENT_LINK
}

data class ReleaseOrderCommand(
    override val transactionId: UUID,
    override val orderId: UUID,
    override val seq: Long,
) : SagaCommand {
    override val type = SagaCommandTypeEnum.RELEASE_ORDER
}

data class CancelOrderCommand(
    override val transactionId: UUID,
    override val orderId: UUID,
    override val seq: Long,
    val reason: CancelOrderReasonEnum = CancelOrderReasonEnum.PAYMENT_NOT_CONFIRMED,
) : SagaCommand {
    override val type = SagaCommandTypeEnum.CANCEL_ORDER
}
