package com.fiap.mechanicalhub.orchestrator.domain.model

import com.fiap.mechanicalhub.orchestrator.domain.enums.PaymentFailureReasonEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStatusEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStepEnum
import com.fiap.mechanicalhub.orchestrator.domain.exceptions.SagaInconsistentStateException
import com.fiap.mechanicalhub.orchestrator.domain.exceptions.SagaTransitionMismatchException
import com.fiap.mechanicalhub.orchestrator.domain.statemachine.SagaStateMachine
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.BUDGET
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.CREATED_AT
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.NOW
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.ORDER_ID
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.PAYMENT_EXPIRES_AT
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.PAYMENT_LINK
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.REPAIRS
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.RESERVED_MATERIALS
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.TRANSACTION_ID
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.saga
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class SagaTest {
    @Test
    fun `given diagnosisCompleted when starting then saga is STARTED at RESERVE_STOCK with seq 1 and emits reserveStock`() {
        val event = DiagnosisCompleted(ORDER_ID, REPAIRS)

        val advance = Saga.start(TRANSACTION_ID, event, NOW)

        assertThat(advance.saga)
            .isEqualTo(
                Saga(
                    transactionId = TRANSACTION_ID,
                    orderId = ORDER_ID,
                    status = SagaStatusEnum.STARTED,
                    currentStep = SagaStepEnum.RESERVE_STOCK,
                    seq = 1,
                    repairs = REPAIRS,
                    createdAt = NOW,
                    updatedAt = NOW,
                ),
            )
        assertThat(advance.stepBefore).isNull()
        assertThat(advance.commands).containsExactly(ReserveStockCommand(TRANSACTION_ID, ORDER_ID, seq = 1, repairs = REPAIRS))
    }

    @Test
    fun `given a command when reading the idempotency key then it is transactionId and STEP`() {
        val command = GeneratePaymentLinkCommand(TRANSACTION_ID, ORDER_ID, 4, BUDGET.budgetId, BUDGET.orderCost)

        val key = command.idempotencyKey

        assertThat(key).isEqualTo("$TRANSACTION_ID:GENERATE_PAYMENT_LINK")
    }

    @Test
    fun `given stockReserved at RESERVE_STOCK when applying then stores materials and emits generateBudget with the next seq`() {
        val current = saga(currentStep = SagaStepEnum.RESERVE_STOCK, seq = 1, reservedMaterials = emptyList(), budget = null)

        val advance = current.apply(StockReserved(TRANSACTION_ID, ORDER_ID, RESERVED_MATERIALS), NOW)

        assertThat(advance?.saga?.reservedMaterials).isEqualTo(RESERVED_MATERIALS)
        assertThat(advance?.saga?.currentStep).isEqualTo(SagaStepEnum.GENERATE_BUDGET)
        assertThat(advance?.saga?.seq).isEqualTo(2)
        assertThat(advance?.commands)
            .containsExactly(GenerateBudgetCommand(TRANSACTION_ID, ORDER_ID, 2, REPAIRS, RESERVED_MATERIALS))
    }

    @Test
    fun `given stockReserved at AWAITING_STOCK when applying then emits clearStockPending then generateBudget with seq n-1 and n`() {
        val current = saga(currentStep = SagaStepEnum.AWAITING_STOCK, seq = 2, reservedMaterials = emptyList(), budget = null)

        val advance = current.apply(StockReserved(TRANSACTION_ID, ORDER_ID, RESERVED_MATERIALS), NOW)

        assertThat(advance?.saga?.seq).isEqualTo(4)
        assertThat(advance?.commands)
            .containsExactly(
                ClearStockPendingCommand(TRANSACTION_ID, ORDER_ID, 3),
                GenerateBudgetCommand(TRANSACTION_ID, ORDER_ID, 4, REPAIRS, RESERVED_MATERIALS),
            )
    }

    @Test
    fun `given stockPending at RESERVE_STOCK when applying then waits for stock and emits markStockPending`() {
        val current = saga(currentStep = SagaStepEnum.RESERVE_STOCK, seq = 1)

        val advance = current.apply(StockPending(TRANSACTION_ID, ORDER_ID), NOW)

        assertThat(advance?.stepAfter).isEqualTo(SagaStepEnum.AWAITING_STOCK)
        assertThat(advance?.commands).containsExactly(MarkStockPendingCommand(TRANSACTION_ID, ORDER_ID, 2))
    }

    @Test
    fun `given budgetGenerated at GENERATE_BUDGET when applying then stores budget and emits sendApproval`() {
        val current = saga(currentStep = SagaStepEnum.GENERATE_BUDGET, seq = 2, budget = null)

        val advance = current.apply(BudgetGenerated(TRANSACTION_ID, ORDER_ID, BUDGET), NOW)

        assertThat(advance?.saga?.budget).isEqualTo(BUDGET)
        assertThat(advance?.commands).containsExactly(SendApprovalCommand(TRANSACTION_ID, ORDER_ID, 3, BUDGET.budgetId, BUDGET.orderCost))
    }

    @Test
    fun `given orderApproved at WAITING_APPROVAL when applying then emits generatePaymentLink with the stored budget`() {
        val current = saga(currentStep = SagaStepEnum.WAITING_APPROVAL, seq = 3)

        val advance = current.apply(OrderApproved(TRANSACTION_ID, ORDER_ID), NOW)

        assertThat(advance?.stepAfter).isEqualTo(SagaStepEnum.AWAITING_PAYMENT)
        assertThat(advance?.commands)
            .containsExactly(GeneratePaymentLinkCommand(TRANSACTION_ID, ORDER_ID, 4, BUDGET.budgetId, BUDGET.orderCost))
    }

    @Test
    fun `given orderRejected at WAITING_APPROVAL when applying then compensates and emits restoreStock with the stored materials`() {
        val current = saga(currentStep = SagaStepEnum.WAITING_APPROVAL, seq = 3)

        val advance = current.apply(OrderRejected(TRANSACTION_ID, ORDER_ID), NOW)

        assertThat(advance?.saga?.status).isEqualTo(SagaStatusEnum.COMPENSATING)
        assertThat(advance?.commands).containsExactly(RestoreStockCommand(TRANSACTION_ID, ORDER_ID, 4, RESERVED_MATERIALS))
    }

    @Test
    fun `given paymentLinkGenerated without link when applying then stores link and emits sendPaymentLink keeping the step`() {
        val current = saga(currentStep = SagaStepEnum.AWAITING_PAYMENT, seq = 4)

        val advance = current.apply(PaymentLinkGenerated(TRANSACTION_ID, ORDER_ID, PAYMENT_LINK, PAYMENT_EXPIRES_AT), NOW)

        assertThat(advance?.saga?.payment).isEqualTo(Payment(link = PAYMENT_LINK, expiresAt = PAYMENT_EXPIRES_AT))
        assertThat(advance?.stepAfter).isEqualTo(SagaStepEnum.AWAITING_PAYMENT)
        assertThat(advance?.commands).containsExactly(SendPaymentLinkCommand(TRANSACTION_ID, ORDER_ID, 5, PAYMENT_LINK))
    }

    @Test
    fun `given paymentLinkGenerated with a link already stored when applying then the step guard discards it`() {
        val current = saga(currentStep = SagaStepEnum.AWAITING_PAYMENT, seq = 5, payment = Payment(link = PAYMENT_LINK))

        val advance = current.apply(PaymentLinkGenerated(TRANSACTION_ID, ORDER_ID, "https://outro", PAYMENT_EXPIRES_AT), NOW)

        assertThat(advance).isNull()
    }

    @Test
    fun `given paymentConfirmed at AWAITING_PAYMENT when applying then keeps the link, stores paymentId and emits releaseOrder`() {
        val current = saga(currentStep = SagaStepEnum.AWAITING_PAYMENT, seq = 5, payment = Payment(PAYMENT_LINK, PAYMENT_EXPIRES_AT))

        val advance = current.apply(PaymentConfirmed(TRANSACTION_ID, ORDER_ID, "pay-123"), NOW)

        assertThat(advance?.saga?.payment).isEqualTo(Payment(PAYMENT_LINK, PAYMENT_EXPIRES_AT, "pay-123"))
        assertThat(advance?.stepAfter).isEqualTo(SagaStepEnum.AWAITING_EXECUTION)
        assertThat(advance?.commands).containsExactly(ReleaseOrderCommand(TRANSACTION_ID, ORDER_ID, 6))
    }

    @Test
    fun `given paymentFailed at AWAITING_PAYMENT when applying then compensates and emits cancelOrder`() {
        val current = saga(currentStep = SagaStepEnum.AWAITING_PAYMENT, seq = 5)

        val advance = current.apply(PaymentFailed(TRANSACTION_ID, ORDER_ID, PaymentFailureReasonEnum.EXPIRED), NOW)

        assertThat(advance?.saga?.status).isEqualTo(SagaStatusEnum.COMPENSATING)
        assertThat(advance?.commands).containsExactly(CancelOrderCommand(TRANSACTION_ID, ORDER_ID, 6))
    }

    @Test
    fun `given orderCanceled at CANCEL_ORDER when applying then emits restoreStock`() {
        val current = saga(currentStep = SagaStepEnum.CANCEL_ORDER, status = SagaStatusEnum.COMPENSATING, seq = 6)

        val advance = current.apply(OrderCanceled(TRANSACTION_ID, ORDER_ID), NOW)

        assertThat(advance?.stepAfter).isEqualTo(SagaStepEnum.RESTORE_STOCK)
        assertThat(advance?.commands).containsExactly(RestoreStockCommand(TRANSACTION_ID, ORDER_ID, 7, RESERVED_MATERIALS))
    }

    @Test
    fun `given orderFinished at AWAITING_EXECUTION when applying then emits consumeStock with the stored materials`() {
        val current = saga(currentStep = SagaStepEnum.AWAITING_EXECUTION, seq = 6)

        val advance = current.apply(OrderFinished(TRANSACTION_ID, ORDER_ID), NOW)

        assertThat(advance?.commands).containsExactly(ConsumeStockCommand(TRANSACTION_ID, ORDER_ID, 7, RESERVED_MATERIALS))
    }

    @Test
    fun `given stockConsumed at CONSUME_STOCK when applying then completes the saga without commands`() {
        val current = saga(currentStep = SagaStepEnum.CONSUME_STOCK, seq = 7)

        val advance = current.apply(StockConsumed(TRANSACTION_ID, ORDER_ID), NOW)

        assertThat(advance?.saga)
            .isEqualTo(current.copy(status = SagaStatusEnum.COMPLETED, currentStep = null, updatedAt = NOW, finishedAt = NOW))
        assertThat(advance?.commands).isEmpty()
    }

    @Test
    fun `given stockRestored at RESTORE_STOCK when applying then the saga is COMPENSATED and finished`() {
        val current = saga(currentStep = SagaStepEnum.RESTORE_STOCK, status = SagaStatusEnum.COMPENSATING, seq = 4)

        val advance = current.apply(StockRestored(TRANSACTION_ID, ORDER_ID), NOW)

        assertThat(advance?.saga?.status).isEqualTo(SagaStatusEnum.COMPENSATED)
        assertThat(advance?.saga?.finished).isTrue()
        assertThat(advance?.saga?.finishedAt).isEqualTo(NOW)
    }

    @Test
    fun `given a duplicated response when applying at the next step then the step guard discards it`() {
        val current = saga(currentStep = SagaStepEnum.WAITING_APPROVAL, seq = 3)

        val advance = current.apply(BudgetGenerated(TRANSACTION_ID, ORDER_ID, BUDGET), NOW)

        assertThat(advance).isNull()
    }

    @Test
    fun `given a finished saga when applying any response then the step guard discards it`() {
        val current = saga(currentStep = null, status = SagaStatusEnum.COMPLETED, seq = 7)

        val advance = current.apply(StockConsumed(TRANSACTION_ID, ORDER_ID), NOW)

        assertThat(advance).isNull()
    }

    @Test
    fun `given an event from another transaction when applying then it is rejected`() {
        val current = saga(currentStep = SagaStepEnum.RESERVE_STOCK)

        val applying = { current.apply(StockPending(UUID.randomUUID(), ORDER_ID), NOW) }

        assertThatThrownBy { applying() }.isInstanceOf(SagaTransitionMismatchException::class.java)
    }

    @Test
    fun `given a saga without budget when approving then the command cannot be built`() {
        val current = saga(currentStep = SagaStepEnum.WAITING_APPROVAL, seq = 3, budget = null)

        val applying = { current.apply(OrderApproved(TRANSACTION_ID, ORDER_ID), NOW) }

        assertThatThrownBy { applying() }.isInstanceOf(SagaInconsistentStateException::class.java)
    }

    @Test
    fun `given a change for another step when applying the change then it is rejected`() {
        val current = saga(currentStep = SagaStepEnum.RESERVE_STOCK)
        val event = BudgetGenerated(TRANSACTION_ID, ORDER_ID, BUDGET)
        val change = SagaChange.of(SagaStateMachine.candidatesFor(event.type).single(), event, NOW)

        val applying = { current.applyChange(change) }

        assertThatThrownBy { applying() }.isInstanceOf(SagaTransitionMismatchException::class.java)
    }

    @Test
    fun `given the creation transition when building a change then it is rejected`() {
        val event = StockPending(TRANSACTION_ID, ORDER_ID)

        val building = { SagaChange.of(SagaStateMachine.creation, event, NOW) }

        assertThatThrownBy { building() }.isInstanceOf(SagaTransitionMismatchException::class.java)
    }

    @Test
    fun `given a transition of another event when building a change then it is rejected`() {
        val transition = SagaStateMachine.candidatesFor(StockPending(TRANSACTION_ID, ORDER_ID).type).single()

        val building = { SagaChange.of(transition, OrderApproved(TRANSACTION_ID, ORDER_ID), NOW) }

        assertThatThrownBy { building() }.isInstanceOf(SagaTransitionMismatchException::class.java)
    }

    @Test
    fun `given budgetGenerated when building the change then it carries the guard, the data and the seq increment`() {
        val transition = SagaStateMachine.candidatesFor(BudgetGenerated(TRANSACTION_ID, ORDER_ID, BUDGET).type).single()

        val change = SagaChange.of(transition, BudgetGenerated(TRANSACTION_ID, ORDER_ID, BUDGET), NOW)

        assertThat(change)
            .isEqualTo(
                SagaChange(
                    expectedStep = SagaStepEnum.GENERATE_BUDGET,
                    requiresPaymentLinkAbsent = false,
                    nextStep = SagaStepEnum.WAITING_APPROVAL,
                    status = SagaStatusEnum.STARTED,
                    seqIncrement = 1,
                    updatedAt = NOW,
                    budget = BUDGET,
                ),
            )
    }

    @Test
    fun `given a saga with seq 7 when reserving seqs for 2 commands then they are 6 and 7`() {
        val current = saga(seq = 7)

        val seqs = current.emittedCommandSeqs(2)

        assertThat(seqs.toList()).containsExactly(6L, 7L)
    }

    @Test
    fun `given a saga created earlier when applying then createdAt is preserved and updatedAt moves`() {
        val current = saga(currentStep = SagaStepEnum.RESERVE_STOCK)

        val advance = current.apply(StockPending(TRANSACTION_ID, ORDER_ID), NOW)

        assertThat(advance?.saga?.createdAt).isEqualTo(CREATED_AT)
        assertThat(advance?.saga?.updatedAt).isEqualTo(NOW)
    }
}
