package com.fiap.mechanicalhub.orchestrator.infrastructure.messaging.events

import com.fiap.mechanicalhub.orchestrator.application.ports.DuplicateMessageException
import com.fiap.mechanicalhub.orchestrator.application.usecases.IncomingSagaEvent
import com.fiap.mechanicalhub.orchestrator.application.usecases.ProcessSagaEventUseCase
import com.fiap.mechanicalhub.orchestrator.application.usecases.SagaEventOutcome
import com.fiap.mechanicalhub.orchestrator.application.usecases.SagaStepResult
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStatusEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStepEnum
import com.fiap.mechanicalhub.orchestrator.domain.model.DiagnosisCompleted
import com.fiap.mechanicalhub.orchestrator.domain.model.Saga
import com.fiap.mechanicalhub.orchestrator.domain.model.SagaAdvance
import com.fiap.mechanicalhub.orchestrator.domain.model.StockPending
import com.fiap.mechanicalhub.orchestrator.domain.statemachine.SagaStateMachine
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.ORDER_ID
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.TRANSACTION_ID
import com.fiap.mechanicalhub.orchestrator.infrastructure.observability.SagaMetrics
import com.mongodb.MongoException
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.util.UUID

class SagaEventMessageProcessorTest {
    private val registry = SimpleMeterRegistry()
    private val useCase = mockk<ProcessSagaEventUseCase>()
    private val parser = SagaEventParser(JsonMapper.builder().addModule(KotlinModule.Builder().build()).build())
    private val processor = SagaEventMessageProcessor(parser, useCase, SagaMetrics(registry))

    @Test
    fun `given a valid event when processing then the use case receives the domain event and the message is acknowledged`() {
        every { useCase.execute(any()) } returns SagaEventOutcome.Duplicate

        val decision = processor.process(stockPendingBody(), receiveCount = 1)

        assertThat(decision).isEqualTo(EventDecision.Acknowledge)
        verify { useCase.execute(IncomingSagaEvent(EVENT_ID, StockPending(TRANSACTION_ID, ORDER_ID))) }
    }

    @Test
    fun `given a step guard discard when processing then the message is acknowledged`() {
        every { useCase.execute(any()) } returns SagaEventOutcome.Discarded(SagaStepResult.Discarded(TRANSACTION_ID, null, null))

        val decision = processor.process(stockPendingBody(), receiveCount = 1)

        assertThat(decision).isEqualTo(EventDecision.Acknowledge)
    }

    @Test
    fun `given a duplicate key race on the history when processing then the message is acknowledged`() {
        every { useCase.execute(any()) } throws DuplicateMessageException(EVENT_ID.toString())

        val decision = processor.process(stockPendingBody(), receiveCount = 1)

        assertThat(decision).isEqualTo(EventDecision.Acknowledge)
    }

    @Test
    fun `given a body out of the contract when processing then it goes to the dead letter queue without calling the use case`() {
        val decision = processor.process("""{"type":"stockPending"}""", receiveCount = 1)

        assertThat(decision).isInstanceOf(EventDecision.DeadLetter::class.java)
        assertThat((decision as EventDecision.DeadLetter).error).isInstanceOf(InvalidMessageException::class.java)
        verify(exactly = 0) { useCase.execute(any()) }
    }

    @Test
    fun `given an unknown type when processing then it goes to the dead letter queue`() {
        val decision = processor.process(stockPendingBody().replace("stockPending", "somethingElse"), receiveCount = 1)

        assertThat(decision).isInstanceOf(EventDecision.DeadLetter::class.java)
    }

    @Test
    fun `given paymentFailed with an unknown reason when processing then it goes to the dead letter queue`() {
        val body = stockPendingBody().replace("\"stockPending\"", "\"paymentFailed\",\"reason\":\"OTHER\"")

        val decision = processor.process(body, receiveCount = 1)

        assertThat((decision as EventDecision.DeadLetter).error).isInstanceOf(InvalidMessageException::class.java)
    }

    @ParameterizedTest(name = "recebimento {0} -> {1} s")
    @CsvSource("1, 1", "2, 2", "3, 4", "4, 8", "5, 8", "6, 8")
    fun `given a transient error when processing then the visibility follows the backoff by receive count`(
        receiveCount: Int,
        expectedDelay: Int,
    ) {
        val writeConflict = MongoException(112, "WriteConflict")
        every { useCase.execute(any()) } throws writeConflict

        val decision = processor.process(stockPendingBody(), receiveCount)

        assertThat(decision).isEqualTo(EventDecision.Retry(expectedDelay, writeConflict))
    }

    @Test
    fun `given a started saga when processing then started and applied counters are incremented`() {
        val advance = Saga.start(TRANSACTION_ID, DiagnosisCompleted(ORDER_ID, SagaFixtures.REPAIRS), SagaFixtures.NOW)
        every { useCase.execute(any()) } returns SagaEventOutcome.Applied(advance)

        processor.process(stockPendingBody(), receiveCount = 1)

        assertThat(registry.counter("saga.started").count()).isEqualTo(1.0)
        assertThat(registry.counter("saga.messages", "type", "stockPending", "outcome", "APPLIED").count()).isEqualTo(1.0)
    }

    @Test
    fun `given a terminal saga when processing then finished counter is incremented by status`() {
        val saga = SagaFixtures.saga(currentStep = null, status = SagaStatusEnum.COMPLETED)
        val advance = SagaAdvance(SagaStateMachine.creation, SagaStepEnum.CONSUME_STOCK, saga, emptyList())
        every { useCase.execute(any()) } returns SagaEventOutcome.Applied(advance)

        processor.process(stockPendingBody(), receiveCount = 1)

        assertThat(registry.counter("saga.finished", "status", "COMPLETED").count()).isEqualTo(1.0)
        assertThat(registry.counter("saga.started").count()).isEqualTo(0.0)
    }

    @Test
    fun `given a discard or a contract violation when processing then the outcome counters are incremented`() {
        every { useCase.execute(any()) } returns SagaEventOutcome.Discarded(SagaStepResult.Discarded(TRANSACTION_ID, null, null))

        processor.process(stockPendingBody(), receiveCount = 1)
        processor.process("not json", receiveCount = 1)

        assertThat(registry.counter("saga.messages", "type", "stockPending", "outcome", "DISCARDED_STEP_GUARD").count()).isEqualTo(1.0)
        assertThat(registry.counter("saga.messages", "type", "unknown", "outcome", "DEAD_LETTER").count()).isEqualTo(1.0)
    }

    private fun stockPendingBody() =
        """
        {"eventId":"$EVENT_ID","transactionId":"$TRANSACTION_ID","orderId":"$ORDER_ID","type":"stockPending",
         "version":1,"timestamp":"2026-10-11T12:00:00Z"}
        """.trimIndent()

    private companion object {
        val EVENT_ID: UUID = UUID.fromString("e0000000-0000-4000-8000-000000000002")
    }
}
