package com.fiap.mechanicalhub.orchestrator.infrastructure.messaging.contracts

import com.fiap.mechanicalhub.orchestrator.domain.enums.PaymentFailureReasonEnum
import com.fiap.mechanicalhub.orchestrator.domain.model.Budget
import com.fiap.mechanicalhub.orchestrator.domain.model.BudgetGenerated
import com.fiap.mechanicalhub.orchestrator.domain.model.CancelOrderCommand
import com.fiap.mechanicalhub.orchestrator.domain.model.ClearStockPendingCommand
import com.fiap.mechanicalhub.orchestrator.domain.model.ConsumeStockCommand
import com.fiap.mechanicalhub.orchestrator.domain.model.DiagnosisCompleted
import com.fiap.mechanicalhub.orchestrator.domain.model.GenerateBudgetCommand
import com.fiap.mechanicalhub.orchestrator.domain.model.GeneratePaymentLinkCommand
import com.fiap.mechanicalhub.orchestrator.domain.model.MarkStockPendingCommand
import com.fiap.mechanicalhub.orchestrator.domain.model.OrderApproved
import com.fiap.mechanicalhub.orchestrator.domain.model.OrderCanceled
import com.fiap.mechanicalhub.orchestrator.domain.model.OrderFinished
import com.fiap.mechanicalhub.orchestrator.domain.model.OrderRejected
import com.fiap.mechanicalhub.orchestrator.domain.model.PaymentConfirmed
import com.fiap.mechanicalhub.orchestrator.domain.model.PaymentFailed
import com.fiap.mechanicalhub.orchestrator.domain.model.PaymentLinkGenerated
import com.fiap.mechanicalhub.orchestrator.domain.model.ReleaseOrderCommand
import com.fiap.mechanicalhub.orchestrator.domain.model.RepairReference
import com.fiap.mechanicalhub.orchestrator.domain.model.ReserveStockCommand
import com.fiap.mechanicalhub.orchestrator.domain.model.ReservedMaterial
import com.fiap.mechanicalhub.orchestrator.domain.model.RestoreStockCommand
import com.fiap.mechanicalhub.orchestrator.domain.model.SagaCommand
import com.fiap.mechanicalhub.orchestrator.domain.model.SagaEvent
import com.fiap.mechanicalhub.orchestrator.domain.model.SendApprovalCommand
import com.fiap.mechanicalhub.orchestrator.domain.model.SendPaymentLinkCommand
import com.fiap.mechanicalhub.orchestrator.domain.model.StockConsumed
import com.fiap.mechanicalhub.orchestrator.domain.model.StockPending
import com.fiap.mechanicalhub.orchestrator.domain.model.StockReserved
import com.fiap.mechanicalhub.orchestrator.domain.model.StockRestored
import java.time.Instant
import java.util.UUID

/**
 * Converte o contrato recebido no evento de dominio. Valor fora do contrato
 * (ex.: `reason` desconhecido) lanca `IllegalArgumentException`: erro
 * permanente, tratado pelo consumidor.
 */
fun EventMessage.toDomain(): SagaEvent =
    when (this) {
        is DiagnosisCompletedMessage -> DiagnosisCompleted(orderId, repairs.map { RepairReference(it.repairId) })
        is StockPendingMessage -> StockPending(transactionId, orderId)
        is StockReservedMessage -> StockReserved(transactionId, orderId, reservedMaterials.map { it.toDomain() })
        is BudgetGeneratedMessage -> BudgetGenerated(transactionId, orderId, Budget(budgetId, orderCost))
        is OrderApprovedMessage -> OrderApproved(transactionId, orderId)
        is OrderRejectedMessage -> OrderRejected(transactionId, orderId)
        is PaymentLinkGeneratedMessage -> PaymentLinkGenerated(transactionId, orderId, paymentLink, expiresAt)
        is PaymentConfirmedMessage -> PaymentConfirmed(transactionId, orderId, paymentId)
        is PaymentFailedMessage -> PaymentFailed(transactionId, orderId, PaymentFailureReasonEnum.valueOf(reason))
        is OrderCanceledMessage -> OrderCanceled(transactionId, orderId)
        is OrderFinishedMessage -> OrderFinished(transactionId, orderId)
        is StockConsumedMessage -> StockConsumed(transactionId, orderId)
        is StockRestoredMessage -> StockRestored(transactionId, orderId)
    }

/** Converte o comando da saga no contrato publicado; `eventId` e o `_id` do documento no outbox. */
fun SagaCommand.toMessage(
    eventId: UUID,
    timestamp: Instant,
): CommandMessage =
    when (this) {
        is ReserveStockCommand -> {
            ReserveStockMessage(eventId, transactionId, orderId, timestamp, idempotencyKey, repairs.map { it.toDto() })
        }

        is GenerateBudgetCommand -> {
            GenerateBudgetMessage(
                eventId = eventId,
                transactionId = transactionId,
                orderId = orderId,
                timestamp = timestamp,
                idempotencyKey = idempotencyKey,
                repairs = repairs.map { it.toDto() },
                reservedMaterials = reservedMaterials.map { it.toDto() },
            )
        }

        is GeneratePaymentLinkCommand -> {
            GeneratePaymentLinkMessage(eventId, transactionId, orderId, timestamp, idempotencyKey, budgetId, orderCost)
        }

        is ConsumeStockCommand -> {
            ConsumeStockMessage(eventId, transactionId, orderId, timestamp, idempotencyKey, reservedMaterials.map { it.toDto() })
        }

        is RestoreStockCommand -> {
            RestoreStockMessage(eventId, transactionId, orderId, timestamp, idempotencyKey, reservedMaterials.map { it.toDto() })
        }

        is SendApprovalCommand -> {
            SendApprovalMessage(eventId, transactionId, orderId, timestamp, idempotencyKey, budgetId, orderCost)
        }

        is MarkStockPendingCommand -> {
            MarkStockPendingMessage(eventId, transactionId, orderId, timestamp, idempotencyKey)
        }

        is ClearStockPendingCommand -> {
            ClearStockPendingMessage(eventId, transactionId, orderId, timestamp, idempotencyKey)
        }

        is SendPaymentLinkCommand -> {
            SendPaymentLinkMessage(eventId, transactionId, orderId, timestamp, idempotencyKey, paymentLink)
        }

        is ReleaseOrderCommand -> {
            ReleaseOrderMessage(eventId, transactionId, orderId, timestamp, idempotencyKey)
        }

        is CancelOrderCommand -> {
            CancelOrderMessage(eventId, transactionId, orderId, timestamp, idempotencyKey, reason.name)
        }
    }

private fun ReservedMaterialDto.toDomain() = ReservedMaterial(materialId, quantity)

private fun ReservedMaterial.toDto() = ReservedMaterialDto(materialId, quantity)

private fun RepairReference.toDto() = RepairReferenceDto(repairId)
