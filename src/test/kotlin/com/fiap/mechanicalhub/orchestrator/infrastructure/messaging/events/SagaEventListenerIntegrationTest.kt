package com.fiap.mechanicalhub.orchestrator.infrastructure.messaging.events

import com.fiap.mechanicalhub.orchestrator.SagaIntegrationTest
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStepEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.TransactionOutcomeEnum
import com.fiap.mechanicalhub.orchestrator.domain.model.Saga
import io.awspring.cloud.sqs.operations.SqsTemplate
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import java.time.Duration
import java.util.UUID

class SagaEventListenerIntegrationTest : SagaIntegrationTest() {
    @Autowired
    private lateinit var sqsTemplate: SqsTemplate

    @Autowired
    private lateinit var sqsAsyncClient: SqsAsyncClient

    @Test
    fun `given an event for the expected step when consumed then the saga advances and the message is deleted`() {
        val saga = givenSaga(SagaStepEnum.RESERVE_STOCK)
        val eventId = UUID.randomUUID()

        sendEvent(saga, eventId, "stockPending")

        await atMost TIMEOUT untilAsserted {
            assertThat(historyOf(saga.orderId).map { it.outcome }).containsExactly(TransactionOutcomeEnum.APPLIED)
        }
        assertThat(storedSaga(saga.transactionId)?.currentStep).isEqualTo(SagaStepEnum.AWAITING_STOCK)
        assertThat(outboxOf(saga.orderId).map { it.eventType }).containsExactly("markStockPending")
        await atMost TIMEOUT untilAsserted { assertThat(messagesInQueue(EVENTS_QUEUE)).isZero() }
    }

    @Test
    fun `given the same event delivered twice when consumed then it is applied once`() {
        val saga = givenSaga(SagaStepEnum.RESERVE_STOCK)
        val eventId = UUID.randomUUID()

        sendEvent(saga, eventId, "stockPending", deduplicationId = UUID.randomUUID())
        sendEvent(saga, eventId, "stockPending", deduplicationId = UUID.randomUUID())
        val probe = UUID.randomUUID()
        sendEvent(saga, probe, "stockPending")

        await atMost TIMEOUT untilAsserted {
            assertThat(historyOf(saga.orderId).map { it.messageKey }).contains(probe.toString())
        }
        assertThat(historyOf(saga.orderId).count { it.messageKey == eventId.toString() }).isEqualTo(1)
        assertThat(storedSaga(saga.transactionId)?.seq).isEqualTo(2)
    }

    @Test
    fun `given an event for another step when consumed then it is discarded by the step guard and not sent to the DLQ`() {
        val saga = givenSaga(SagaStepEnum.GENERATE_BUDGET, seq = 3)

        sendEvent(saga, UUID.randomUUID(), "stockPending")

        await atMost TIMEOUT untilAsserted {
            assertThat(historyOf(saga.orderId).map { it.outcome }).containsExactly(TransactionOutcomeEnum.DISCARDED_STEP_GUARD)
        }
        assertThat(storedSaga(saga.transactionId)).isEqualTo(saga)
        assertThat(outboxOf(saga.orderId)).isEmpty()
    }

    @Test
    fun `given a payload out of the contract when consumed then it goes to the DLQ with the error`() {
        val saga = givenSaga(SagaStepEnum.RESERVE_STOCK)

        sendEvent(saga, UUID.randomUUID(), "stockReserved")

        val deadLetter = await atMost TIMEOUT untilNotNull { deadLetterFor(saga) }
        assertThat(deadLetter.messageAttributes()["errorClass"]?.stringValue()).isEqualTo(InvalidMessageException::class.java.name)
        assertThat(deadLetter.attributes()[MessageSystemAttributeName.MESSAGE_GROUP_ID]).isEqualTo(saga.orderId.toString())
        assertThat(historyOf(saga.orderId)).isEmpty()
    }

    @Test
    fun `given an event type without handler when consumed then it goes to the DLQ`() {
        val saga = givenSaga(SagaStepEnum.WAITING_APPROVAL)

        sendEvent(saga, UUID.randomUUID(), "orderApproved")

        val deadLetter = await atMost TIMEOUT untilNotNull { deadLetterFor(saga) }
        assertThat(deadLetter.messageAttributes()["errorClass"]?.stringValue()).endsWith("UnsupportedSagaEventException")
        assertThat(storedSaga(saga.transactionId)).isEqualTo(saga)
    }

    private fun sendEvent(
        saga: Saga,
        eventId: UUID,
        type: String,
        deduplicationId: UUID = eventId,
    ) {
        val body =
            """
            {"eventId":"$eventId","transactionId":"${saga.transactionId}","orderId":"${saga.orderId}","type":"$type",
             "version":1,"timestamp":"2026-10-11T12:00:00Z"}
            """.trimIndent()
        sqsTemplate.send<String> {
            it
                .queue(EVENTS_QUEUE)
                .payload(body)
                .messageGroupId(saga.orderId.toString())
                .messageDeduplicationId(deduplicationId.toString())
        }
    }

    private fun deadLetterFor(saga: Saga) =
        sqsAsyncClient
            .receiveMessage {
                it
                    .queueUrl(queueUrl(DLQ))
                    .maxNumberOfMessages(MAX_MESSAGES)
                    .messageSystemAttributeNames(MessageSystemAttributeName.ALL)
                    .messageAttributeNames("All")
                    .visibilityTimeout(0)
                    .waitTimeSeconds(1)
            }.join()
            .messages()
            .firstOrNull { it.body().contains(saga.orderId.toString()) }

    private fun messagesInQueue(queueName: String): Int {
        val attributes =
            sqsAsyncClient
                .getQueueAttributes {
                    it.queueUrl(queueUrl(queueName)).attributeNames(
                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE,
                    )
                }.join()
                .attributes()
        return attributes.values.sumOf { it.toInt() }
    }

    private fun queueUrl(queueName: String) = sqsAsyncClient.getQueueUrl { it.queueName(queueName) }.join().queueUrl()

    private companion object {
        const val EVENTS_QUEUE = "mechanical-hub-orchestrator-events.fifo"
        const val DLQ = "mechanical-hub-orchestrator-events-dlq.fifo"
        const val MAX_MESSAGES = 10
        val TIMEOUT: Duration = Duration.ofSeconds(30)
    }
}
