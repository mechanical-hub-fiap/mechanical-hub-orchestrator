package com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.mappers

import com.fiap.mechanicalhub.orchestrator.application.ports.TransactionHistoryEntry
import com.fiap.mechanicalhub.orchestrator.domain.model.Budget
import com.fiap.mechanicalhub.orchestrator.domain.model.Payment
import com.fiap.mechanicalhub.orchestrator.domain.model.RepairReference
import com.fiap.mechanicalhub.orchestrator.domain.model.ReservedMaterial
import com.fiap.mechanicalhub.orchestrator.domain.model.Saga
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.documents.BudgetDocument
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.documents.PaymentDocument
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.documents.RepairDocument
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.documents.ReservedMaterialDocument
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.documents.TransactionHistoryDocument
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.documents.TransactionRegisterDocument
import java.util.UUID

fun Saga.toDocument() =
    TransactionRegisterDocument(
        id = transactionId.toString(),
        orderId = orderId.toString(),
        status = status,
        currentStep = currentStep,
        seq = seq,
        repairs = repairs.map { RepairDocument(it.repairId.toString()) },
        reservedMaterials = reservedMaterials.map { it.toDocument() },
        budget = budget?.toDocument(),
        payment = payment?.toDocument(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        finishedAt = finishedAt,
    )

fun TransactionRegisterDocument.toDomain() =
    Saga(
        transactionId = UUID.fromString(id),
        orderId = UUID.fromString(orderId),
        status = status,
        currentStep = currentStep,
        seq = seq,
        repairs = repairs.map { RepairReference(UUID.fromString(it.repairId)) },
        reservedMaterials = reservedMaterials.map { ReservedMaterial(UUID.fromString(it.materialId), it.quantity) },
        budget = budget?.let { Budget(UUID.fromString(it.budgetId), it.orderCost) },
        payment = payment?.let { Payment(it.link, it.expiresAt, it.paymentId) },
        createdAt = createdAt,
        updatedAt = updatedAt,
        finishedAt = finishedAt,
    )

fun ReservedMaterial.toDocument() = ReservedMaterialDocument(materialId.toString(), quantity)

fun Budget.toDocument() = BudgetDocument(budgetId.toString(), orderCost)

fun Payment.toDocument() = PaymentDocument(link, expiresAt, paymentId)

fun TransactionHistoryEntry.toDocument() =
    TransactionHistoryDocument(
        transactionId = transactionId?.toString(),
        orderId = orderId.toString(),
        seq = seq,
        messageType = messageType,
        messageKey = messageKey,
        stepBefore = stepBefore,
        stepAfter = stepAfter,
        outcome = outcome,
        receivedAt = receivedAt,
    )
