package com.fiap.mechanicalhub.orchestrator.application.usecases

import com.fiap.mechanicalhub.orchestrator.SagaIntegrationTest
import com.fiap.mechanicalhub.orchestrator.application.ports.DuplicateMessageException
import com.fiap.mechanicalhub.orchestrator.application.ports.TransactionHistoryEntry
import com.fiap.mechanicalhub.orchestrator.application.ports.TransactionHistoryPort
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStepEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.TransactionOutcomeEnum
import com.fiap.mechanicalhub.orchestrator.domain.model.Saga
import com.fiap.mechanicalhub.orchestrator.domain.model.StockPending
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.documents.OutboxEventDocument
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.documents.OutboxStatusEnum
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import java.time.Instant
import java.util.UUID

class ProcessSagaEventIntegrationTest : SagaIntegrationTest() {
    @Autowired
    private lateinit var useCase: ProcessSagaEventUseCase

    @Autowired
    private lateinit var transactionHistory: TransactionHistoryPort

    @Test
    fun `given saga in the expected step when processing then saga, history and outbox are written together`() {
        val saga = givenSaga(SagaStepEnum.RESERVE_STOCK, seq = 1)
        val eventId = UUID.randomUUID()

        val outcome = useCase.execute(stockPending(saga, eventId))

        assertThat(outcome).isInstanceOf(SagaEventOutcome.Applied::class.java)
        val stored = storedSaga(saga.transactionId)
        assertThat(stored?.currentStep).isEqualTo(SagaStepEnum.AWAITING_STOCK)
        assertThat(stored?.seq).isEqualTo(2)
        val history = historyOf(saga.orderId).single()
        assertThat(history.messageKey).isEqualTo(eventId.toString())
        assertThat(history.outcome).isEqualTo(TransactionOutcomeEnum.APPLIED)
        assertThat(history.stepBefore to history.stepAfter).isEqualTo(SagaStepEnum.RESERVE_STOCK to SagaStepEnum.AWAITING_STOCK)
        assertThat(history.seq).isEqualTo(2)
        val outbox = outboxOf(saga.orderId).single()
        assertThat(outbox.eventType to outbox.seq).isEqualTo("markStockPending" to 2L)
        assertThat(outbox.status).isEqualTo(OutboxStatusEnum.PENDING)
        assertThat(outbox.payload).contains("\"idempotencyKey\":\"${saga.transactionId}:MARK_STOCK_PENDING\"")
        assertThat(outbox.payload).contains("\"eventId\":\"${outbox.id}\"")
    }

    @Test
    fun `given saga in another step when processing then only the history is written as DISCARDED_STEP_GUARD`() {
        val saga = givenSaga(SagaStepEnum.GENERATE_BUDGET, seq = 3)

        val outcome = useCase.execute(stockPending(saga, UUID.randomUUID()))

        assertThat(outcome).isInstanceOf(SagaEventOutcome.Discarded::class.java)
        assertThat(storedSaga(saga.transactionId)?.seq).isEqualTo(3)
        val history = historyOf(saga.orderId).single()
        assertThat(history.outcome).isEqualTo(TransactionOutcomeEnum.DISCARDED_STEP_GUARD)
        assertThat(history.stepBefore).isEqualTo(SagaStepEnum.GENERATE_BUDGET)
        assertThat(outboxOf(saga.orderId)).isEmpty()
    }

    @Test
    fun `given an event already processed when processing it again then it is a duplicate and nothing changes`() {
        val saga = givenSaga(SagaStepEnum.RESERVE_STOCK)
        val incoming = stockPending(saga, UUID.randomUUID())
        useCase.execute(incoming)

        val outcome = useCase.execute(incoming)

        assertThat(outcome).isEqualTo(SagaEventOutcome.Duplicate)
        assertThat(historyOf(saga.orderId)).hasSize(1)
        assertThat(outboxOf(saga.orderId)).hasSize(1)
    }

    @Test
    fun `given a failure writing the outbox when processing then neither saga nor history are persisted`() {
        val saga = givenSaga(SagaStepEnum.RESERVE_STOCK, seq = 1)
        mongoTemplate.insert(conflictingOutbox(saga, seq = 2))

        val processing = { useCase.execute(stockPending(saga, UUID.randomUUID())) }

        assertThatThrownBy { processing() }.isInstanceOf(DuplicateKeyException::class.java)
        val stored = storedSaga(saga.transactionId)
        assertThat(stored?.currentStep to stored?.seq).isEqualTo(SagaStepEnum.RESERVE_STOCK to 1L)
        assertThat(historyOf(saga.orderId)).isEmpty()
    }

    @Test
    fun `given a messageKey already in the history when appending it again then it is reported as duplicate`() {
        val saga = givenSaga(SagaStepEnum.GENERATE_BUDGET)
        val incoming = stockPending(saga, UUID.randomUUID())
        useCase.execute(incoming)
        val entry =
            TransactionHistoryEntry(
                transactionId = saga.transactionId,
                orderId = saga.orderId,
                seq = 1,
                messageType = "stockPending",
                messageKey = incoming.eventId.toString(),
                stepBefore = null,
                stepAfter = null,
                outcome = TransactionOutcomeEnum.APPLIED,
                receivedAt = Instant.now(),
            )

        val appending = { transactionHistory.append(entry) }

        assertThatThrownBy { appending() }.isInstanceOf(DuplicateMessageException::class.java)
    }

    private fun stockPending(
        saga: Saga,
        eventId: UUID,
    ) = IncomingSagaEvent(eventId, StockPending(saga.transactionId, saga.orderId))

    private fun conflictingOutbox(
        saga: Saga,
        seq: Long,
    ) = OutboxEventDocument(
        id = UUID.randomUUID().toString(),
        orderId = saga.orderId.toString(),
        seq = seq,
        eventType = "reserveStock",
        payload = "{}",
        status = OutboxStatusEnum.PENDING,
        attempts = 0,
        lastError = null,
        createdAt = Instant.now(),
        sentAt = null,
    )
}
