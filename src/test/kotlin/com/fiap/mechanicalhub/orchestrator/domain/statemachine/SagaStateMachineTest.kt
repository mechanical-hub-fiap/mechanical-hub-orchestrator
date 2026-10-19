package com.fiap.mechanicalhub.orchestrator.domain.statemachine

import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaCommandTypeEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaEventTypeEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStatusEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStepEnum
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class SagaStateMachineTest {
    @ParameterizedTest(name = "{0} em {1}")
    @MethodSource("specTable")
    fun `given an event and the expected step when looking up the transition then it mirrors the spec table`(
        eventType: SagaEventTypeEnum,
        currentStep: SagaStepEnum,
        expected: SagaTransition,
    ) {
        val transition = SagaStateMachine.transitionFor(eventType, currentStep)

        assertThat(transition).isEqualTo(expected)
    }

    @Test
    fun `given every event and every step outside the spec table when looking up the transition then the step guard discards it`() {
        val acceptedPairs = specRows().map { it.eventType to it.expectedStep }.toSet()
        val allPairs = SagaEventTypeEnum.entries.flatMap { event -> (SagaStepEnum.entries + null).map { step -> event to step } }

        val discardedPairs = allPairs.filter { (event, step) -> SagaStateMachine.transitionFor(event, step) == null }

        assertThat(discardedPairs).containsExactlyInAnyOrderElementsOf(allPairs - acceptedPairs)
    }

    @Test
    fun `given stockReserved when listing candidates then AWAITING_STOCK guard is tried before RESERVE_STOCK`() {
        val candidates = SagaStateMachine.candidatesFor(SagaEventTypeEnum.STOCK_RESERVED)

        assertThat(candidates.map { it.expectedStep to it.seqIncrement })
            .containsExactly(SagaStepEnum.AWAITING_STOCK to 2, SagaStepEnum.RESERVE_STOCK to 1)
    }

    @Test
    fun `given diagnosisCompleted when reading the creation transition then it starts the saga at RESERVE_STOCK with reserveStock`() {
        val creation = SagaStateMachine.creation

        assertThat(creation)
            .isEqualTo(
                SagaTransition(
                    eventType = SagaEventTypeEnum.DIAGNOSIS_COMPLETED,
                    expectedStep = null,
                    nextStep = SagaStepEnum.RESERVE_STOCK,
                    nextStatus = SagaStatusEnum.STARTED,
                    commands = listOf(SagaCommandTypeEnum.RESERVE_STOCK),
                ),
            )
    }

    @Test
    fun `given diagnosisCompleted when listing guarded candidates then there is none`() {
        val candidates = SagaStateMachine.candidatesFor(SagaEventTypeEnum.DIAGNOSIS_COMPLETED)

        assertThat(candidates).isEmpty()
    }

    companion object {
        @JvmStatic
        fun specTable(): List<Arguments> = specRows().map { Arguments.of(it.eventType, it.expectedStep, it) }

        /** Linhas da secao 3 da spec, escritas a mao para conferir a tabela do dominio. */
        private fun specRows(): List<SagaTransition> =
            listOf(
                row(SagaEventTypeEnum.STOCK_PENDING, SagaStepEnum.RESERVE_STOCK, SagaStepEnum.AWAITING_STOCK, SagaStatusEnum.STARTED)
                    .withCommands(SagaCommandTypeEnum.MARK_STOCK_PENDING),
                row(SagaEventTypeEnum.STOCK_RESERVED, SagaStepEnum.RESERVE_STOCK, SagaStepEnum.GENERATE_BUDGET, SagaStatusEnum.STARTED)
                    .withCommands(SagaCommandTypeEnum.GENERATE_BUDGET),
                row(SagaEventTypeEnum.STOCK_RESERVED, SagaStepEnum.AWAITING_STOCK, SagaStepEnum.GENERATE_BUDGET, SagaStatusEnum.STARTED)
                    .withCommands(SagaCommandTypeEnum.CLEAR_STOCK_PENDING, SagaCommandTypeEnum.GENERATE_BUDGET),
                row(SagaEventTypeEnum.BUDGET_GENERATED, SagaStepEnum.GENERATE_BUDGET, SagaStepEnum.WAITING_APPROVAL, SagaStatusEnum.STARTED)
                    .withCommands(SagaCommandTypeEnum.SEND_APPROVAL),
                row(SagaEventTypeEnum.ORDER_APPROVED, SagaStepEnum.WAITING_APPROVAL, SagaStepEnum.AWAITING_PAYMENT, SagaStatusEnum.STARTED)
                    .withCommands(SagaCommandTypeEnum.GENERATE_PAYMENT_LINK),
                row(
                    SagaEventTypeEnum.ORDER_REJECTED,
                    SagaStepEnum.WAITING_APPROVAL,
                    SagaStepEnum.RESTORE_STOCK,
                    SagaStatusEnum.COMPENSATING,
                ).withCommands(SagaCommandTypeEnum.RESTORE_STOCK),
                row(
                    SagaEventTypeEnum.PAYMENT_LINK_GENERATED,
                    SagaStepEnum.AWAITING_PAYMENT,
                    SagaStepEnum.AWAITING_PAYMENT,
                    SagaStatusEnum.STARTED,
                ).withCommands(SagaCommandTypeEnum.SEND_PAYMENT_LINK).copy(requiresPaymentLinkAbsent = true),
                row(
                    SagaEventTypeEnum.PAYMENT_CONFIRMED,
                    SagaStepEnum.AWAITING_PAYMENT,
                    SagaStepEnum.AWAITING_EXECUTION,
                    SagaStatusEnum.STARTED,
                ).withCommands(SagaCommandTypeEnum.RELEASE_ORDER),
                row(SagaEventTypeEnum.PAYMENT_FAILED, SagaStepEnum.AWAITING_PAYMENT, SagaStepEnum.CANCEL_ORDER, SagaStatusEnum.COMPENSATING)
                    .withCommands(SagaCommandTypeEnum.CANCEL_ORDER),
                row(SagaEventTypeEnum.ORDER_CANCELED, SagaStepEnum.CANCEL_ORDER, SagaStepEnum.RESTORE_STOCK, SagaStatusEnum.COMPENSATING)
                    .withCommands(SagaCommandTypeEnum.RESTORE_STOCK),
                row(SagaEventTypeEnum.ORDER_FINISHED, SagaStepEnum.AWAITING_EXECUTION, SagaStepEnum.CONSUME_STOCK, SagaStatusEnum.STARTED)
                    .withCommands(SagaCommandTypeEnum.CONSUME_STOCK),
                row(SagaEventTypeEnum.STOCK_CONSUMED, SagaStepEnum.CONSUME_STOCK, null, SagaStatusEnum.COMPLETED),
                row(SagaEventTypeEnum.STOCK_RESTORED, SagaStepEnum.RESTORE_STOCK, null, SagaStatusEnum.COMPENSATED),
            )

        private fun row(
            eventType: SagaEventTypeEnum,
            expectedStep: SagaStepEnum,
            nextStep: SagaStepEnum?,
            nextStatus: SagaStatusEnum,
        ) = SagaTransition(eventType, expectedStep, nextStep, nextStatus, commands = emptyList())

        private fun SagaTransition.withCommands(vararg commands: SagaCommandTypeEnum) = copy(commands = commands.toList())
    }
}
