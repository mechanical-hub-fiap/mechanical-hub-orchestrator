package com.fiap.mechanicalhub.orchestrator.application.usecases

import com.fiap.mechanicalhub.orchestrator.SagaIntegrationTest
import com.fiap.mechanicalhub.orchestrator.domain.enums.PaymentFailureReasonEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStatusEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStepEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.TransactionOutcomeEnum
import com.fiap.mechanicalhub.orchestrator.domain.model.BudgetGenerated
import com.fiap.mechanicalhub.orchestrator.domain.model.DiagnosisCompleted
import com.fiap.mechanicalhub.orchestrator.domain.model.OrderApproved
import com.fiap.mechanicalhub.orchestrator.domain.model.OrderCanceled
import com.fiap.mechanicalhub.orchestrator.domain.model.OrderFinished
import com.fiap.mechanicalhub.orchestrator.domain.model.OrderRejected
import com.fiap.mechanicalhub.orchestrator.domain.model.PaymentConfirmed
import com.fiap.mechanicalhub.orchestrator.domain.model.PaymentFailed
import com.fiap.mechanicalhub.orchestrator.domain.model.PaymentLinkGenerated
import com.fiap.mechanicalhub.orchestrator.domain.model.SagaEvent
import com.fiap.mechanicalhub.orchestrator.domain.model.StockConsumed
import com.fiap.mechanicalhub.orchestrator.domain.model.StockPending
import com.fiap.mechanicalhub.orchestrator.domain.model.StockReserved
import com.fiap.mechanicalhub.orchestrator.domain.model.StockRestored
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID

/** Handlers reais de ponta a ponta no pipeline transacional (MongoDB replica set). */
class SagaHandlersFlowIntegrationTest : SagaIntegrationTest() {
    @Autowired
    private lateinit var useCase: ProcessSagaEventUseCase

    private fun process(event: SagaEvent) = useCase.execute(IncomingSagaEvent(UUID.randomUUID(), event))

    private fun start(): Pair<UUID, UUID> {
        val orderId = UUID.randomUUID()
        val outcome = process(DiagnosisCompleted(orderId, SagaFixtures.REPAIRS)) as SagaEventOutcome.Applied
        return outcome.advance.saga.transactionId to orderId
    }

    private fun commandsOf(orderId: UUID) = outboxOf(orderId).map { it.eventType to it.seq }

    @Test
    fun `given the happy path when events arrive in order then the saga completes and emits each command`() {
        val (tx, order) = start()
        process(StockReserved(tx, order, SagaFixtures.RESERVED_MATERIALS))
        process(BudgetGenerated(tx, order, SagaFixtures.BUDGET))
        process(OrderApproved(tx, order))
        process(PaymentLinkGenerated(tx, order, SagaFixtures.PAYMENT_LINK, SagaFixtures.PAYMENT_EXPIRES_AT))
        process(PaymentConfirmed(tx, order, "pay-1"))
        process(OrderFinished(tx, order))
        process(StockConsumed(tx, order))

        val saga = storedSaga(tx)!!
        assertThat(saga.status).isEqualTo(SagaStatusEnum.COMPLETED)
        assertThat(saga.currentStep).isNull()
        assertThat(saga.finishedAt).isNotNull()
        assertThat(saga.payment?.paymentId).isEqualTo("pay-1")
        assertThat(commandsOf(order))
            .containsExactly(
                "reserveStock" to 1L,
                "generateBudget" to 2L,
                "sendApproval" to 3L,
                "generatePaymentLink" to 4L,
                "sendPaymentLink" to 5L,
                "releaseOrder" to 6L,
                "consumeStock" to 7L,
            )
        assertThat(historyOf(order)).hasSize(8).allMatch { it.outcome == TransactionOutcomeEnum.APPLIED }
    }

    @Test
    fun `given stock pending when stock is reserved later then clearStockPending precedes generateBudget`() {
        val (tx, order) = start()
        process(StockPending(tx, order))
        process(StockReserved(tx, order, SagaFixtures.RESERVED_MATERIALS))

        assertThat(storedSaga(tx)?.currentStep).isEqualTo(SagaStepEnum.GENERATE_BUDGET)
        assertThat(commandsOf(order))
            .containsExactly("reserveStock" to 1L, "markStockPending" to 2L, "clearStockPending" to 3L, "generateBudget" to 4L)
    }

    @Test
    fun `given a rejected budget when stock is restored then the saga is compensated`() {
        val (tx, order) = start()
        process(StockReserved(tx, order, SagaFixtures.RESERVED_MATERIALS))
        process(BudgetGenerated(tx, order, SagaFixtures.BUDGET))
        process(OrderRejected(tx, order))
        assertThat(storedSaga(tx)?.status).isEqualTo(SagaStatusEnum.COMPENSATING)
        process(StockRestored(tx, order))

        val saga = storedSaga(tx)!!
        assertThat(saga.status).isEqualTo(SagaStatusEnum.COMPENSATED)
        assertThat(saga.currentStep).isNull()
        assertThat(commandsOf(order).map { it.first }).endsWith("restoreStock")
    }

    @Test
    fun `given a payment failure when the order is canceled then stock is restored and the saga is compensated`() {
        val (tx, order) = start()
        process(StockReserved(tx, order, SagaFixtures.RESERVED_MATERIALS))
        process(BudgetGenerated(tx, order, SagaFixtures.BUDGET))
        process(OrderApproved(tx, order))
        process(PaymentFailed(tx, order, PaymentFailureReasonEnum.entries.first()))
        assertThat(storedSaga(tx)?.currentStep).isEqualTo(SagaStepEnum.CANCEL_ORDER)
        process(OrderCanceled(tx, order))
        process(StockRestored(tx, order))

        assertThat(storedSaga(tx)?.status).isEqualTo(SagaStatusEnum.COMPENSATED)
        assertThat(commandsOf(order).map { it.first }).endsWith("cancelOrder", "restoreStock")
    }

    @Test
    fun `given diagnosisCompleted repeated for the same order when processing then it is discarded and no second saga exists`() {
        val (tx, order) = start()

        val outcome = process(DiagnosisCompleted(order, SagaFixtures.REPAIRS))

        assertThat(outcome).isInstanceOf(SagaEventOutcome.Discarded::class.java)
        assertThat(storedSaga(tx)?.seq).isEqualTo(1)
        assertThat(commandsOf(order)).containsExactly("reserveStock" to 1L)
        assertThat(historyOf(order).map { it.outcome })
            .containsExactlyInAnyOrder(TransactionOutcomeEnum.APPLIED, TransactionOutcomeEnum.DISCARDED_STEP_GUARD)
    }

    @Test
    fun `given an out of order event when processing then it is discarded`() {
        val (tx, order) = start()

        val outcome = process(OrderApproved(tx, order))

        assertThat(outcome).isInstanceOf(SagaEventOutcome.Discarded::class.java)
        assertThat(storedSaga(tx)?.currentStep).isEqualTo(SagaStepEnum.RESERVE_STOCK)
    }
}
