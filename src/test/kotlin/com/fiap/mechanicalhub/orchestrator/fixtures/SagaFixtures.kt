package com.fiap.mechanicalhub.orchestrator.fixtures

import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStatusEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStepEnum
import com.fiap.mechanicalhub.orchestrator.domain.model.Budget
import com.fiap.mechanicalhub.orchestrator.domain.model.Payment
import com.fiap.mechanicalhub.orchestrator.domain.model.RepairReference
import com.fiap.mechanicalhub.orchestrator.domain.model.ReservedMaterial
import com.fiap.mechanicalhub.orchestrator.domain.model.Saga
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

object SagaFixtures {
    val TRANSACTION_ID: UUID = UUID.fromString("6f2c1d0e-8a4b-4c1e-9d3f-1b2a3c4d5e6f")
    val ORDER_ID: UUID = UUID.fromString("a1b2c3d4-0000-4000-8000-000000000001")
    val CREATED_AT: Instant = Instant.parse("2026-10-06T10:00:00Z")
    val NOW: Instant = Instant.parse("2026-10-06T11:00:00Z")
    val PAYMENT_EXPIRES_AT: Instant = Instant.parse("2026-10-07T12:05:00Z")
    const val PAYMENT_LINK = "https://www.mercadopago.com.br/checkout/abc"

    val REPAIRS: List<RepairReference> =
        listOf(
            RepairReference(UUID.fromString("00000000-0000-4000-8000-0000000000a1")),
            RepairReference(UUID.fromString("00000000-0000-4000-8000-0000000000a2")),
        )

    val RESERVED_MATERIALS: List<ReservedMaterial> =
        listOf(
            ReservedMaterial(UUID.fromString("00000000-0000-4000-8000-0000000000b1"), quantity = 4),
            ReservedMaterial(UUID.fromString("00000000-0000-4000-8000-0000000000b2"), quantity = 2),
        )

    val BUDGET = Budget(UUID.fromString("00000000-0000-4000-8000-0000000000c1"), BigDecimal("350.00"))

    fun saga(
        currentStep: SagaStepEnum? = SagaStepEnum.RESERVE_STOCK,
        status: SagaStatusEnum = SagaStatusEnum.STARTED,
        seq: Long = 1,
        reservedMaterials: List<ReservedMaterial> = RESERVED_MATERIALS,
        budget: Budget? = BUDGET,
        payment: Payment? = null,
    ) = Saga(
        transactionId = TRANSACTION_ID,
        orderId = ORDER_ID,
        status = status,
        currentStep = currentStep,
        seq = seq,
        repairs = REPAIRS,
        reservedMaterials = reservedMaterials,
        budget = budget,
        payment = payment,
        createdAt = CREATED_AT,
        updatedAt = CREATED_AT,
    )
}
