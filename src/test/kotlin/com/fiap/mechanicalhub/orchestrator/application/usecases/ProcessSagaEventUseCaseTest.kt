package com.fiap.mechanicalhub.orchestrator.application.usecases

import com.fiap.mechanicalhub.orchestrator.application.ports.OutboxPort
import com.fiap.mechanicalhub.orchestrator.application.ports.TransactionHistoryEntry
import com.fiap.mechanicalhub.orchestrator.application.ports.TransactionHistoryPort
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaEventTypeEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStepEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.TransactionOutcomeEnum
import com.fiap.mechanicalhub.orchestrator.domain.model.OrderApproved
import com.fiap.mechanicalhub.orchestrator.domain.model.SagaAdvance
import com.fiap.mechanicalhub.orchestrator.domain.model.SagaEvent
import com.fiap.mechanicalhub.orchestrator.domain.model.StockPending
import com.fiap.mechanicalhub.orchestrator.domain.statemachine.SagaStateMachine
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.NOW
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.ORDER_ID
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.TRANSACTION_ID
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.saga
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ProcessSagaEventUseCaseTest {
    private val transactionHistory = mockk<TransactionHistoryPort>()
    private val outbox = mockk<OutboxPort>()
    private val clock = Clock.fixed(NOW, ZoneOffset.UTC)

    @Test
    fun `given an already registered messageKey when processing then it is a duplicate and nothing is written`() {
        val handler = FakeHandler { error("nao deve ser chamado") }
        every { transactionHistory.existsByMessageKey(EVENT_ID.toString()) } returns true

        val outcome = useCase(handler).execute(IncomingSagaEvent(EVENT_ID, STOCK_PENDING))

        assertThat(outcome).isEqualTo(SagaEventOutcome.Duplicate)
        assertThat(handler.calls).isZero()
        verify(exactly = 0) { transactionHistory.append(any()) }
        verify(exactly = 0) { outbox.append(any(), any()) }
    }

    @Test
    fun `given an applied step when processing then history is APPLIED and the commands go to the outbox`() {
        val advance = appliedAdvance()
        givenNewMessage()

        val outcome = useCase(FakeHandler { SagaStepResult.Applied(advance) }).execute(IncomingSagaEvent(EVENT_ID, STOCK_PENDING))

        assertThat(outcome).isEqualTo(SagaEventOutcome.Applied(advance))
        verify {
            transactionHistory.append(
                TransactionHistoryEntry(
                    transactionId = TRANSACTION_ID,
                    orderId = ORDER_ID,
                    seq = 2,
                    messageType = "stockPending",
                    messageKey = EVENT_ID.toString(),
                    stepBefore = SagaStepEnum.RESERVE_STOCK,
                    stepAfter = SagaStepEnum.AWAITING_STOCK,
                    outcome = TransactionOutcomeEnum.APPLIED,
                    receivedAt = NOW,
                ),
            )
            outbox.append(advance.commands, NOW)
        }
    }

    @Test
    fun `given a step guard discard when processing then only the history is written as DISCARDED_STEP_GUARD`() {
        val discarded = SagaStepResult.Discarded(TRANSACTION_ID, SagaStepEnum.GENERATE_BUDGET, 3)
        givenNewMessage()

        val outcome = useCase(FakeHandler { discarded }).execute(IncomingSagaEvent(EVENT_ID, STOCK_PENDING))

        assertThat(outcome).isEqualTo(SagaEventOutcome.Discarded(discarded))
        verify {
            transactionHistory.append(
                match {
                    it.outcome == TransactionOutcomeEnum.DISCARDED_STEP_GUARD &&
                        it.stepBefore == SagaStepEnum.GENERATE_BUDGET &&
                        it.stepAfter == SagaStepEnum.GENERATE_BUDGET &&
                        it.seq == 3L
                },
            )
        }
        verify(exactly = 0) { outbox.append(any(), any()) }
    }

    @Test
    fun `given an event without handler when processing then it fails as unsupported`() {
        givenNewMessage()
        val incoming = IncomingSagaEvent(EVENT_ID, OrderApproved(TRANSACTION_ID, ORDER_ID))

        val processing = { useCase(FakeHandler { error("nao deve ser chamado") }).execute(incoming) }

        assertThatThrownBy { processing() }.isInstanceOf(UnsupportedSagaEventException::class.java)
    }

    private fun givenNewMessage() {
        every { transactionHistory.existsByMessageKey(EVENT_ID.toString()) } returns false
        justRun { transactionHistory.append(any()) }
        justRun { outbox.append(any(), any()) }
    }

    private fun useCase(handler: SagaEventHandler) =
        ProcessSagaEventUseCase(SagaEventDispatcher(listOf(handler)), transactionHistory, outbox, clock)

    private fun appliedAdvance(): SagaAdvance {
        val transition = SagaStateMachine.transitionFor(SagaEventTypeEnum.STOCK_PENDING, SagaStepEnum.RESERVE_STOCK)
        val advanced = saga(currentStep = SagaStepEnum.AWAITING_STOCK, seq = 2)
        return SagaAdvance(requireNotNull(transition), SagaStepEnum.RESERVE_STOCK, advanced, advanced.commandsFor(transition))
    }

    private class FakeHandler(
        private val result: () -> SagaStepResult,
    ) : SagaEventHandler {
        var calls = 0
            private set

        override val eventType = SagaEventTypeEnum.STOCK_PENDING

        override fun handle(
            event: SagaEvent,
            now: Instant,
        ): SagaStepResult {
            calls++
            return result()
        }
    }

    private companion object {
        val EVENT_ID: UUID = UUID.fromString("e0000000-0000-4000-8000-000000000001")
        val STOCK_PENDING = StockPending(TRANSACTION_ID, ORDER_ID)
    }
}
