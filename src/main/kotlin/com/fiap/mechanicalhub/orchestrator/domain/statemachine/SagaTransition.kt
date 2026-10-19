package com.fiap.mechanicalhub.orchestrator.domain.statemachine

import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaCommandTypeEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaEventTypeEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStatusEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStepEnum

/**
 * Uma linha da tabela da secao 3 da spec: evento + passo esperado -> proximo
 * passo, status e comandos emitidos (na ordem de publicacao).
 *
 * `expectedStep = null` so na criacao da saga (`diagnosisCompleted`).
 * `requiresPaymentLinkAbsent` completa a guarda do `paymentLinkGenerated`
 * (`payment.link = null`).
 */
data class SagaTransition(
    val eventType: SagaEventTypeEnum,
    val expectedStep: SagaStepEnum?,
    val nextStep: SagaStepEnum?,
    val nextStatus: SagaStatusEnum,
    val commands: List<SagaCommandTypeEnum>,
    val requiresPaymentLinkAbsent: Boolean = false,
) {
    val createsSaga: Boolean get() = expectedStep == null

    /** `n` do `$inc: { seq: n }`: um `seq` por comando gravado no outbox. */
    val seqIncrement: Int get() = commands.size
}
