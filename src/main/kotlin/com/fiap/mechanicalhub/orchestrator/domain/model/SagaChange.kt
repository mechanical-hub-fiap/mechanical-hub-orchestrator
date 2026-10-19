package com.fiap.mechanicalhub.orchestrator.domain.model

import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStatusEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStepEnum
import com.fiap.mechanicalhub.orchestrator.domain.exceptions.SagaTransitionMismatchException
import com.fiap.mechanicalhub.orchestrator.domain.statemachine.SagaTransition
import java.time.Instant

/**
 * Alteracao atomica de uma saga existente, derivada de uma transicao e do
 * evento recebido. Descreve a guarda (`expectedStep`, `payment.link = null`),
 * os campos do `$set` (os de dados so quando nao nulos, alem de passo, status,
 * `updatedAt` e `finishedAt`) e o `$inc` de `seq`, sem depender do Mongo.
 */
data class SagaChange(
    val expectedStep: SagaStepEnum,
    val requiresPaymentLinkAbsent: Boolean,
    val nextStep: SagaStepEnum?,
    val status: SagaStatusEnum,
    val seqIncrement: Int,
    val updatedAt: Instant,
    val finishedAt: Instant? = null,
    val reservedMaterials: List<ReservedMaterial>? = null,
    val budget: Budget? = null,
    val paymentLink: String? = null,
    val paymentExpiresAt: Instant? = null,
    val paymentId: String? = null,
) {
    companion object {
        fun of(
            transition: SagaTransition,
            event: SagaResponseEvent,
            now: Instant,
        ): SagaChange {
            val expectedStep =
                transition.expectedStep
                    ?: throw SagaTransitionMismatchException("A transicao de criacao nao gera alteracao de saga existente")
            if (transition.eventType != event.type) {
                throw SagaTransitionMismatchException("Evento ${event.type} aplicado a transicao de ${transition.eventType}")
            }
            val change =
                SagaChange(
                    expectedStep = expectedStep,
                    requiresPaymentLinkAbsent = transition.requiresPaymentLinkAbsent,
                    nextStep = transition.nextStep,
                    status = transition.nextStatus,
                    seqIncrement = transition.seqIncrement,
                    updatedAt = now,
                    finishedAt = now.takeIf { transition.nextStatus.terminal },
                )
            return change.withDataOf(event)
        }

        private fun SagaChange.withDataOf(event: SagaResponseEvent): SagaChange =
            when (event) {
                is StockReserved -> copy(reservedMaterials = event.reservedMaterials)

                is BudgetGenerated -> copy(budget = event.budget)

                is PaymentLinkGenerated -> copy(paymentLink = event.paymentLink, paymentExpiresAt = event.expiresAt)

                is PaymentConfirmed -> copy(paymentId = event.paymentId)

                is StockPending,
                is OrderApproved,
                is OrderRejected,
                is PaymentFailed,
                is OrderCanceled,
                is OrderFinished,
                is StockConsumed,
                is StockRestored,
                -> this
            }
    }
}
