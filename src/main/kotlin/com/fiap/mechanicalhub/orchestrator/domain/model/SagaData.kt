package com.fiap.mechanicalhub.orchestrator.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Reparo recebido em `diagnosisCompleted`. */
data class RepairReference(
    val repairId: UUID,
)

/** Material reservado recebido em `stockReserved`; usado no `consumeStock` e no `restoreStock`. */
data class ReservedMaterial(
    val materialId: UUID,
    val quantity: Int,
)

/** Orcamento recebido em `budgetGenerated`. */
data class Budget(
    val budgetId: UUID,
    val orderCost: BigDecimal,
)

/** `link` e `expiresAt` chegam em `paymentLinkGenerated`; `paymentId` em `paymentConfirmed`. */
data class Payment(
    val link: String? = null,
    val expiresAt: Instant? = null,
    val paymentId: String? = null,
)
