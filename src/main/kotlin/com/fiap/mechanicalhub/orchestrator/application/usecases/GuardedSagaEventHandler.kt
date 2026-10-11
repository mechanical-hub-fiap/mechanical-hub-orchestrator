package com.fiap.mechanicalhub.orchestrator.application.usecases

import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaEventTypeEnum
import com.fiap.mechanicalhub.orchestrator.domain.exceptions.SagaTransitionMismatchException
import com.fiap.mechanicalhub.orchestrator.domain.model.SagaEvent
import com.fiap.mechanicalhub.orchestrator.domain.model.SagaResponseEvent
import java.time.Instant

/**
 * Handler dos eventos de resposta de uma saga existente. O que muda em cada
 * evento (passo esperado, dados, comandos) vem da tabela da maquina de estados;
 * o handler so aplica a guarda de passo via [SagaStepGuard].
 */
class GuardedSagaEventHandler(
    override val eventType: SagaEventTypeEnum,
    private val guard: SagaStepGuard,
) : SagaEventHandler {
    override fun handle(
        event: SagaEvent,
        now: Instant,
    ): SagaStepResult {
        val response =
            event as? SagaResponseEvent
                ?: throw SagaTransitionMismatchException("Evento ${event.type.messageType} nao e resposta de uma saga existente")
        return guard.advance(response, now)
    }
}
