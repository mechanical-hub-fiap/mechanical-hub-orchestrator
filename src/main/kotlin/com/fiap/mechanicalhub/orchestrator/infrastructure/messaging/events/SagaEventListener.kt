package com.fiap.mechanicalhub.orchestrator.infrastructure.messaging.events

import io.awspring.cloud.sqs.annotation.SqsListener
import io.awspring.cloud.sqs.listener.SqsHeaders
import io.awspring.cloud.sqs.listener.Visibility
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/**
 * Consumidor de `mechanical-hub-orchestrator-events.fifo` (confirmacao manual).
 * A mensagem so e apagada depois do `commitTransaction`; em erro transitorio
 * ela fica na fila, bloqueando o grupo (`orderId`) ate a proxima tentativa.
 * O contexto de trace (`traceparent`) e extraido dos message attributes pelo
 * agente OpenTelemetry; os logs das decisoes saem no processador.
 */
@Component
class SagaEventListener(
    private val processor: SagaEventMessageProcessor,
    private val deadLetterPublisher: EventDeadLetterPublisher,
) {
    @SqsListener(queueNames = ["\${mechanical-hub.orchestrator.queues.orchestrator-events}"])
    fun onMessage(
        message: Message<String>,
        acknowledgement: Acknowledgement,
        visibility: Visibility,
    ) {
        val receiveCount =
            message.headers[SqsHeaders.MessageSystemAttributes.SQS_APPROXIMATE_RECEIVE_COUNT]?.toString()?.toIntOrNull() ?: 1

        when (val decision = processor.process(message.payload, receiveCount)) {
            EventDecision.Acknowledge -> {
                acknowledgement.acknowledge()
            }

            is EventDecision.Retry -> {
                visibility.changeTo(decision.delaySeconds)
            }

            is EventDecision.DeadLetter -> {
                deadLetterPublisher.publish(message, decision.error)
                acknowledgement.acknowledge()
            }
        }
    }
}
