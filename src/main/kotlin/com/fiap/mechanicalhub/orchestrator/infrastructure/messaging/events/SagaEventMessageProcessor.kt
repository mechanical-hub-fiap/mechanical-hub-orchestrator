package com.fiap.mechanicalhub.orchestrator.infrastructure.messaging.events

import com.fiap.mechanicalhub.orchestrator.application.usecases.ProcessSagaEventUseCase
import com.fiap.mechanicalhub.orchestrator.application.usecases.SagaEventOutcome
import com.fiap.mechanicalhub.orchestrator.domain.model.SagaResponseEvent
import com.fiap.mechanicalhub.orchestrator.infrastructure.observability.LogContext
import com.fiap.mechanicalhub.orchestrator.infrastructure.observability.MessageOutcome
import com.fiap.mechanicalhub.orchestrator.infrastructure.observability.SagaMetrics
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
 * (`maxReceiveCount = 6`). Registra as metricas `saga_*` e coloca `eventId`,
 * `orderId`, `transactionId` e `currentStep` no MDC durante o processamento.
 */
@Component
class SagaEventMessageProcessor(
    private val parser: SagaEventParser,
    private val processSagaEvent: ProcessSagaEventUseCase,
    private val metrics: SagaMetrics,
) {
    fun process(
        body: String,
        receiveCount: Int,
    ): EventDecision {
        val parsed = runCatching { parser.parse(body) }
        val incoming = parsed.getOrNull()
        val type = incoming?.event?.type?.messageType ?: UNKNOWN_TYPE
        return LogContext.with(
            LogContext.EVENT_ID to incoming?.eventId,
            LogContext.ORDER_ID to incoming?.event?.orderId,
            LogContext.TRANSACTION_ID to (incoming?.event as? SagaResponseEvent)?.transactionId,
            LogContext.CURRENT_STEP to null,
        ) {
            parsed
                .mapCatching { processSagaEvent.execute(it) }
                .fold(
                    onSuccess = { decide(type, it) },
                    onFailure = { error -> decideOnError(type, error, receiveCount) },
                )
        }
    }

    private fun decide(
        type: String,
        outcome: SagaEventOutcome,
    ): EventDecision {
        when (outcome) {
            is SagaEventOutcome.Applied -> {
                val saga = outcome.advance.saga
                LogContext.put(LogContext.TRANSACTION_ID, saga.transactionId)
                LogContext.put(LogContext.CURRENT_STEP, saga.currentStep)
                if (outcome.advance.stepBefore == null) metrics.sagaStarted()
                if (saga.status.terminal) metrics.sagaFinished(saga.status)
                metrics.messageProcessed(type, MessageOutcome.APPLIED)
                log.debug("Evento aplicado: passo {} -> {}", outcome.advance.stepBefore, outcome.advance.stepAfter)
            }

            is SagaEventOutcome.Discarded -> {
                LogContext.put(LogContext.CURRENT_STEP, outcome.result.currentStep)
                metrics.messageProcessed(type, MessageOutcome.DISCARDED_STEP_GUARD)
                log.info("Evento descartado pela guarda de passo (DISCARDED_STEP_GUARD), passo atual {}", outcome.result.currentStep)
            }

            SagaEventOutcome.Duplicate -> {
                metrics.messageProcessed(type, MessageOutcome.DUPLICATE)
                log.info("Evento duplicado ignorado (transaction_history)")
            }
        }
        return EventDecision.Acknowledge
    }

    private fun decideOnError(
        type: String,
        error: Throwable,
        receiveCount: Int,
    ): EventDecision =
        when (EventErrorClassifier.classify(error)) {
            EventErrorClass.DUPLICATE -> {
                metrics.messageProcessed(type, MessageOutcome.DUPLICATE)
                log.info("Evento duplicado ignorado (E11000 em transaction_history.messageKey)")
                EventDecision.Acknowledge
            }

            EventErrorClass.TRANSIENT -> {
                val delay = backoffSeconds(receiveCount)
                metrics.messageProcessed(type, MessageOutcome.RETRY)
                log.warn("Erro transitorio no evento (recebimento {}), nova tentativa em {} s: {}", receiveCount, delay, error.toString())
                EventDecision.Retry(delay, error)
            }

            EventErrorClass.PERMANENT -> {
                metrics.messageProcessed(type, MessageOutcome.DEAD_LETTER)
                log.error("Erro permanente no evento, enviado a DLQ: {}", error.toString())
                EventDecision.DeadLetter(error)
            }
        }

    private companion object {
        const val UNKNOWN_TYPE = "unknown"
        val BACKOFF_SECONDS = listOf(1, 2, 4, 8, 8)
        val log = LoggerFactory.getLogger(SagaEventMessageProcessor::class.java)

        fun backoffSeconds(receiveCount: Int): Int = BACKOFF_SECONDS[(receiveCount - 1).coerceIn(BACKOFF_SECONDS.indices)]
    }
}
