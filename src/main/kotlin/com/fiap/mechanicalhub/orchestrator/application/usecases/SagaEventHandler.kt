package com.fiap.mechanicalhub.orchestrator.application.usecases

import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaEventTypeEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStepEnum
import com.fiap.mechanicalhub.orchestrator.domain.model.SagaAdvance
import com.fiap.mechanicalhub.orchestrator.domain.model.SagaEvent
import java.time.Instant
import java.util.UUID

/** Resultado de um handler dentro da transacao da mensagem. */
sealed interface SagaStepResult {
    /** Saga criada ou atualizada: grava `APPLIED` no historico e os comandos no outbox. */
    data class Applied(
        val advance: SagaAdvance,
    ) : SagaStepResult

    /**
     * Guarda de passo recusou o evento: grava so o historico com
     * `DISCARDED_STEP_GUARD`. `currentStep` e `seq` sao os da saga no momento
     * (nulos se ela nao existir).
     */
    data class Discarded(
        val transactionId: UUID?,
        val currentStep: SagaStepEnum?,
        val seq: Long?,
    ) : SagaStepResult
}

/**
 * Handler de um tipo de evento. Roda dentro da transacao aberta por
 * [ProcessSagaEventUseCase]; nao grava historico nem outbox (o pipeline grava).
 */
interface SagaEventHandler {
    val eventType: SagaEventTypeEnum

    fun handle(
        event: SagaEvent,
        now: Instant,
    ): SagaStepResult
}
