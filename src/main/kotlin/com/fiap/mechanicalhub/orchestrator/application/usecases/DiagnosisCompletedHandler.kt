package com.fiap.mechanicalhub.orchestrator.application.usecases

import com.fiap.mechanicalhub.orchestrator.application.ports.SagaRepository
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaEventTypeEnum
import com.fiap.mechanicalhub.orchestrator.domain.exceptions.SagaTransitionMismatchException
import com.fiap.mechanicalhub.orchestrator.domain.model.DiagnosisCompleted
import com.fiap.mechanicalhub.orchestrator.domain.model.Saga
import com.fiap.mechanicalhub.orchestrator.domain.model.SagaEvent
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * `diagnosisCompleted`: cria a saga (`STARTED`, `RESERVE_STOCK`, `seq = 1`) e
 * emite `reserveStock`. Ordem que ja tem saga e descartada (`orderId` unico):
 * a checagem previa evita o `E11000`, que abortaria a transacao e impediria
 * gravar o historico do descarte.
 */
@Component
class DiagnosisCompletedHandler(
    private val sagaRepository: SagaRepository,
) : SagaEventHandler {
    override val eventType = SagaEventTypeEnum.DIAGNOSIS_COMPLETED

    override fun handle(
        event: SagaEvent,
        now: Instant,
    ): SagaStepResult {
        val diagnosis =
            event as? DiagnosisCompleted
                ?: throw SagaTransitionMismatchException("Evento ${event.type.messageType} nao abre saga")
        sagaRepository.findByOrderId(diagnosis.orderId)?.let { existing ->
            return SagaStepResult.Discarded(existing.transactionId, existing.currentStep, existing.seq)
        }
        val advance = Saga.start(UUID.randomUUID(), diagnosis, now)
        sagaRepository.insert(advance.saga)
        return SagaStepResult.Applied(advance)
    }
}
