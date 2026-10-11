package com.fiap.mechanicalhub.orchestrator.application.usecases

import com.fiap.mechanicalhub.orchestrator.application.ports.SagaRepository
import com.fiap.mechanicalhub.orchestrator.domain.model.SagaAdvance
import com.fiap.mechanicalhub.orchestrator.domain.model.SagaChange
import com.fiap.mechanicalhub.orchestrator.domain.model.SagaResponseEvent
import com.fiap.mechanicalhub.orchestrator.domain.statemachine.SagaStateMachine
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Guarda de passo da secao 3 da spec: tenta as transicoes candidatas do evento
 * na ordem da maquina de estados (ex.: `stockReserved` tenta `AWAITING_STOCK`
 * e depois `RESERVE_STOCK`), cada uma como um `findOneAndUpdate` condicional.
 * Nenhuma aceitou -> [SagaStepResult.Discarded] com o passo atual da saga.
 */
@Component
class SagaStepGuard(
    private val sagaRepository: SagaRepository,
) {
    fun advance(
        event: SagaResponseEvent,
        now: Instant,
    ): SagaStepResult {
        val applied =
            SagaStateMachine.candidatesFor(event.type).firstNotNullOfOrNull { transition ->
                sagaRepository
                    .applyChange(event.transactionId, SagaChange.of(transition, event, now))
                    ?.let { updated ->
                        SagaAdvance(
                            transition = transition,
                            stepBefore = transition.expectedStep,
                            saga = updated,
                            commands = updated.commandsFor(transition),
                        )
                    }
            }
        if (applied != null) return SagaStepResult.Applied(applied)

        val current = sagaRepository.findById(event.transactionId)
        return SagaStepResult.Discarded(event.transactionId, current?.currentStep, current?.seq)
    }
}
