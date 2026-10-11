package com.fiap.mechanicalhub.orchestrator.infrastructure.messaging.outbox

import com.fiap.mechanicalhub.orchestrator.application.ports.CommandPublisher
import com.fiap.mechanicalhub.orchestrator.application.ports.OutboxMessage
import com.fiap.mechanicalhub.orchestrator.domain.enums.CommandDestinationEnum
import com.fiap.mechanicalhub.orchestrator.infrastructure.config.OrchestratorProperties
import io.awspring.cloud.sqs.operations.SqsTemplate
import org.springframework.stereotype.Component

/** `MessageGroupId = orderId`, `MessageDeduplicationId = eventId` (spec secao 3). */
@Component
class SqsCommandPublisher(
    private val sqsTemplate: SqsTemplate,
    private val properties: OrchestratorProperties,
) : CommandPublisher {
    override fun publish(
        destination: CommandDestinationEnum,
        message: OutboxMessage,
    ) {
        val queue =
            when (destination) {
                CommandDestinationEnum.ORDERS -> properties.queues.ordersCommands
                CommandDestinationEnum.STOCK -> properties.queues.stockCommands
                CommandDestinationEnum.BILLING -> properties.queues.billingCommands
            }
        sqsTemplate.send<String> { options ->
            options
                .queue(queue)
                .payload(message.payload)
                .messageGroupId(message.orderId)
                .messageDeduplicationId(message.eventId)
        }
    }
}
