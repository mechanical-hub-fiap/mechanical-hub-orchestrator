package com.fiap.mechanicalhub.orchestrator.infrastructure.persistence

import com.fiap.mechanicalhub.orchestrator.SagaIntegrationTest
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStatusEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStepEnum
import com.fiap.mechanicalhub.orchestrator.domain.model.PaymentLinkGenerated
import com.fiap.mechanicalhub.orchestrator.domain.model.SagaChange
import com.fiap.mechanicalhub.orchestrator.domain.model.SagaResponseEvent
import com.fiap.mechanicalhub.orchestrator.domain.model.StockConsumed
import com.fiap.mechanicalhub.orchestrator.domain.model.StockReserved
import com.fiap.mechanicalhub.orchestrator.domain.statemachine.SagaStateMachine
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.BUDGET
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.PAYMENT_EXPIRES_AT
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.PAYMENT_LINK
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.RESERVED_MATERIALS
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.temporal.ChronoUnit

class MongoSagaRepositoryIntegrationTest : SagaIntegrationTest() {
    @Autowired
    private lateinit var repository: MongoSagaRepository

    @Test
    fun `given saga in the expected step when applying the change then it returns the saga after the update`() {
        val saga = givenSaga(SagaStepEnum.AWAITING_STOCK, seq = 2)
        val event = StockReserved(saga.transactionId, saga.orderId, RESERVED_MATERIALS)

        val updated = repository.applyChange(saga.transactionId, changeFor(event, SagaStepEnum.AWAITING_STOCK))

        assertThat(updated?.currentStep).isEqualTo(SagaStepEnum.GENERATE_BUDGET)
        assertThat(updated?.seq).isEqualTo(4)
        assertThat(updated?.reservedMaterials).isEqualTo(RESERVED_MATERIALS)
        assertThat(updated?.budget).isEqualTo(BUDGET)
        assertThat(storedSaga(saga.transactionId)).isEqualTo(updated)
    }

    @Test
    fun `given saga in another step when applying the change then nothing is updated`() {
        val saga = givenSaga(SagaStepEnum.GENERATE_BUDGET, seq = 3)
        val event = StockReserved(saga.transactionId, saga.orderId, RESERVED_MATERIALS)

        val updated = repository.applyChange(saga.transactionId, changeFor(event, SagaStepEnum.RESERVE_STOCK))

        assertThat(updated).isNull()
        assertThat(storedSaga(saga.transactionId)).isEqualTo(saga)
    }

    @Test
    fun `given a payment link already stored when applying paymentLinkGenerated again then the guard refuses it`() {
        val saga = givenSaga(SagaStepEnum.AWAITING_PAYMENT, seq = 5)
        val event = PaymentLinkGenerated(saga.transactionId, saga.orderId, PAYMENT_LINK, PAYMENT_EXPIRES_AT)
        val change = changeFor(event, SagaStepEnum.AWAITING_PAYMENT)
        val first = repository.applyChange(saga.transactionId, change)

        val second = repository.applyChange(saga.transactionId, change)

        assertThat(first?.payment?.link).isEqualTo(PAYMENT_LINK)
        assertThat(first?.payment?.expiresAt).isEqualTo(PAYMENT_EXPIRES_AT)
        assertThat(second).isNull()
        assertThat(storedSaga(saga.transactionId)?.seq).isEqualTo(6)
    }

    @Test
    fun `given the last step when applying the change then the saga finishes without step`() {
        val saga = givenSaga(SagaStepEnum.CONSUME_STOCK, seq = 7)
        val change = changeFor(StockConsumed(saga.transactionId, saga.orderId), SagaStepEnum.CONSUME_STOCK)

        val updated = repository.applyChange(saga.transactionId, change)

        assertThat(updated?.status).isEqualTo(SagaStatusEnum.COMPLETED)
        assertThat(updated?.currentStep).isNull()
        assertThat(updated?.finishedAt).isEqualTo(NOW)
        assertThat(updated?.seq).isEqualTo(7)
    }

    private fun changeFor(
        event: SagaResponseEvent,
        expectedStep: SagaStepEnum,
    ): SagaChange {
        val transition = requireNotNull(SagaStateMachine.transitionFor(event.type, expectedStep))
        return SagaChange.of(transition, event, NOW)
    }

    private companion object {
        val NOW: Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)
    }
}
