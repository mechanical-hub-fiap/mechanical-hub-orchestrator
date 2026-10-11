package com.fiap.mechanicalhub.orchestrator.infrastructure.messaging.events

import com.fiap.mechanicalhub.orchestrator.application.usecases.ProcessSagaEventUseCase
import com.fiap.mechanicalhub.orchestrator.application.usecases.SagaEventOutcome
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** O que o listener faz com a mensagem depois do processamento. */
sealed interface EventDecision {
    /** `commitTransaction` feito, duplicata ou descarte pela guarda: apagar a mensagem. */
    data object Acknowledge : EventDecision

    /** Erro transitorio: nao apagar; nova tentativa apos `delaySeconds`. */
    data class Retry(
        val delaySeconds: Int,
        val error: Throwable,
    ) : EventDecision

    /** Erro permanente: enviar a DLQ e apagar da fila de origem. */
    data class DeadLetter(
        val error: Throwable,
    ) : EventDecision
}

/**
 * Parse -> caso de uso (transacao) -> decisao, isolado do SDK para ser testado
 * sem SQS. Retry bloqueante por `ApproximateReceiveCount`: 1 s, 2 s, 4 s, 8 s,
 * 8 s (failure_handling.md §2.2); o 7o recebimento vai sozinho para a DLQ
 * (`maxReceiveCount = 6`).
 */
@Component
class SagaEventMessageProcessor(
    private val parser: SagaEventParser,
    private val processSagaEvent: ProcessSagaEventUseCase,
) {
    fun process(
        body: String,
        receiveCount: Int,
    ): EventDecision =
        runCatching { processSagaEvent.execute(parser.parse(body)) }
            .fold(
                onSuccess = ::decide,
                onFailure = { error -> decideOnError(error, receiveCount) },
            )

    private fun decide(outcome: SagaEventOutcome): EventDecision {
        when (outcome) {
            is SagaEventOutcome.Applied -> {
                log.debug("Evento aplicado: passo {} -> {}", outcome.advance.stepBefore, outcome.advance.stepAfter)
            }

            is SagaEventOutcome.Discarded -> {
                log.info("Evento descartado pela guarda de passo (DISCARDED_STEP_GUARD), passo atual {}", outcome.result.currentStep)
            }

            SagaEventOutcome.Duplicate -> {
                log.info("Evento duplicado ignorado (transaction_history)")
            }
        }
        return EventDecision.Acknowledge
    }

    private fun decideOnError(
        error: Throwable,
        receiveCount: Int,
    ): EventDecision =
        when (EventErrorClassifier.classify(error)) {
            EventErrorClass.DUPLICATE -> {
                log.info("Evento duplicado ignorado (E11000 em transaction_history.messageKey)")
                EventDecision.Acknowledge
            }

            EventErrorClass.TRANSIENT -> {
                EventDecision.Retry(backoffSeconds(receiveCount), error)
            }

            EventErrorClass.PERMANENT -> {
                EventDecision.DeadLetter(error)
            }
        }

    private companion object {
        val BACKOFF_SECONDS = listOf(1, 2, 4, 8, 8)
        val log = LoggerFactory.getLogger(SagaEventMessageProcessor::class.java)

        fun backoffSeconds(receiveCount: Int): Int = BACKOFF_SECONDS[(receiveCount - 1).coerceIn(BACKOFF_SECONDS.indices)]
    }
}
