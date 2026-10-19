package com.fiap.mechanicalhub.orchestrator.application.usecases

import com.fiap.mechanicalhub.orchestrator.application.ports.SagaRepository
import com.fiap.mechanicalhub.orchestrator.application.ports.TransactionHistoryEntry
import com.fiap.mechanicalhub.orchestrator.application.ports.TransactionHistoryQueryPort
import com.fiap.mechanicalhub.orchestrator.domain.enums.TransactionOutcomeEnum
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GetTransactionUseCaseTest {
    private val sagaRepository = mockk<SagaRepository>()
    private val historyQuery = mockk<TransactionHistoryQueryPort>()
    private val useCase = GetTransactionUseCase(sagaRepository, historyQuery)

    @Test
    fun `given an existing saga when querying then it returns the saga with its history`() {
        val saga = SagaFixtures.saga()
        val entry =
            TransactionHistoryEntry(
                saga.transactionId,
                saga.orderId,
                1,
                "diagnosisCompleted",
                "e1",
                null,
                saga.currentStep,
                TransactionOutcomeEnum.APPLIED,
                SagaFixtures.NOW,
            )
        every { sagaRepository.findByOrderId(saga.orderId) } returns saga
        every { historyQuery.findByOrderId(saga.orderId) } returns listOf(entry)

        val view = useCase.execute(saga.orderId)

        assertThat(view).isEqualTo(TransactionView(saga, listOf(entry)))
    }

    @Test
    fun `given an unknown order when querying then it returns null`() {
        every { sagaRepository.findByOrderId(SagaFixtures.ORDER_ID) } returns null

        assertThat(useCase.execute(SagaFixtures.ORDER_ID)).isNull()
    }
}
