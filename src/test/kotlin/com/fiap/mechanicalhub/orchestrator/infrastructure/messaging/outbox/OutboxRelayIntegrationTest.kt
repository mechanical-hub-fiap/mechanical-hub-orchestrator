package com.fiap.mechanicalhub.orchestrator.infrastructure.messaging.outbox

import com.fiap.mechanicalhub.orchestrator.SagaIntegrationTest
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.documents.OutboxEventDocument
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.documents.OutboxStatusEnum
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.Message
import java.time.Duration
import java.time.Instant
import java.util.UUID

class OutboxRelayIntegrationTest : SagaIntegrationTest() {
    @Autowired
    private lateinit var sqsAsyncClient: SqsAsyncClient

    @Test
    fun `given pending outbox events when the relay runs then they reach the routed queues in seq order and are marked SENT`() {
        val orderId = UUID.randomUUID().toString()
        val createdAt = Instant.now()
        insert(orderId, 1, "clearStockPending", createdAt)
        insert(orderId, 2, "generateBudget", createdAt)

        await atMost TIMEOUT untilAsserted {
            val stored = mongoTemplate.findAll(OutboxEventDocument::class.java).filter { it.orderId == orderId }
            assertThat(stored.map { it.status }).containsOnly(OutboxStatusEnum.SENT)
            assertThat(stored.map { it.sentAt }).doesNotContainNull()
        }
        await atMost TIMEOUT untilAsserted {
            assertThat(messagesFor(ORDERS_QUEUE, orderId)).hasSize(1)
            assertThat(messagesFor(BILLING_QUEUE, orderId)).hasSize(1)
        }
        assertThat(messagesFor(ORDERS_QUEUE, orderId).single().body()).contains("clearStockPending")
    }

    private fun insert(
        orderId: String,
        seq: Long,
        type: String,
        createdAt: Instant,
    ) {
        val eventId = UUID.randomUUID().toString()
        mongoTemplate.insert(
            OutboxEventDocument(
                id = eventId,
                orderId = orderId,
                seq = seq,
                eventType = type,
                payload = """{"eventId":"$eventId","orderId":"$orderId","type":"$type"}""",
                status = OutboxStatusEnum.PENDING,
                attempts = 0,
                lastError = null,
                createdAt = createdAt,
                sentAt = null,
            ),
        )
    }

    private fun messagesFor(
        queueName: String,
        orderId: String,
    ): List<Message> {
        val url = sqsAsyncClient.getQueueUrl { it.queueName(queueName) }.join().queueUrl()
        return sqsAsyncClient
            .receiveMessage {
                it
                    .queueUrl(url)
                    .maxNumberOfMessages(MAX_MESSAGES)
                    .visibilityTimeout(0)
                    .waitTimeSeconds(1)
            }.join()
            .messages()
            .filter { it.body().contains(orderId) }
    }

    private companion object {
        const val ORDERS_QUEUE = "mechanical-hub-orders-commands.fifo"
        const val BILLING_QUEUE = "mechanical-hub-billing-commands.fifo"
        const val MAX_MESSAGES = 10
        val TIMEOUT: Duration = Duration.ofSeconds(30)
    }
}
