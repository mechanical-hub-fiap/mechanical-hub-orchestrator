package com.fiap.mechanicalhub.orchestrator.application.usecases

import com.fiap.mechanicalhub.orchestrator.application.ports.SagaRepository
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaCommandTypeEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStepEnum
import com.fiap.mechanicalhub.orchestrator.domain.model.SagaChange
import com.fiap.mechanicalhub.orchestrator.domain.model.StockPending
import com.fiap.mechanicalhub.orchestrator.domain.model.StockReserved
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.NOW
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.ORDER_ID
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.RESERVED_MATERIALS
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.TRANSACTION_ID
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.saga
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SagaStepGuardTest {
    private val sagaRepository = mockk<SagaRepository>()
    private val guard = SagaStepGuard(sagaRepository)

    @Test
    fun `given saga in the expected step when advancing then it returns the applied change with the reserved seqs`() {
        val stored = saga(currentStep = SagaStepEnum.RESERVE_STOCK, seq = 1)
        every { sagaRepository.applyChange(TRANSACTION_ID, any()) } answers { stored.applyChange(secondArg()) }

        val result = guard.advance(StockPending(TRANSACTION_ID, ORDER_ID), NOW)

        val advance = (result as SagaStepResult.Applied).advance
        assertThat(advance.stepBefore).isEqualTo(SagaStepEnum.RESERVE_STOCK)
        assertThat(advance.stepAfter).isEqualTo(SagaStepEnum.AWAITING_STOCK)
        assertThat(advance.commands.map { it.type to it.seq }).containsExactly(SagaCommandTypeEnum.MARK_STOCK_PENDING to 2L)
    }

    @Test
    fun `given stockReserved with saga in RESERVE_STOCK when advancing then AWAITING_STOCK is tried first and RESERVE_STOCK applies`() {
        val stored = saga(currentStep = SagaStepEnum.RESERVE_STOCK, seq = 1, reservedMaterials = emptyList())
        every { sagaRepository.applyChange(TRANSACTION_ID, any()) } answers {
            secondArg<SagaChange>().takeIf(stored::accepts)?.let(stored::applyChange)
        }

        val result = guard.advance(StockReserved(TRANSACTION_ID, ORDER_ID, RESERVED_MATERIALS), NOW)

        verifyOrder {
            sagaRepository.applyChange(TRANSACTION_ID, match { it.expectedStep == SagaStepEnum.AWAITING_STOCK })
            sagaRepository.applyChange(TRANSACTION_ID, match { it.expectedStep == SagaStepEnum.RESERVE_STOCK })
        }
        val advance = (result as SagaStepResult.Applied).advance
        assertThat(advance.commands.map { it.type }).containsExactly(SagaCommandTypeEnum.GENERATE_BUDGET)
    }

    @Test
    fun `given saga in another step when advancing then it is discarded with the current step and seq`() {
        every { sagaRepository.applyChange(TRANSACTION_ID, any()) } returns null
        every { sagaRepository.findById(TRANSACTION_ID) } returns saga(currentStep = SagaStepEnum.GENERATE_BUDGET, seq = 3)

        val result = guard.advance(StockPending(TRANSACTION_ID, ORDER_ID), NOW)

        assertThat(result).isEqualTo(SagaStepResult.Discarded(TRANSACTION_ID, SagaStepEnum.GENERATE_BUDGET, 3))
    }

    @Test
    fun `given an unknown transaction when advancing then it is discarded without step nor seq`() {
        every { sagaRepository.applyChange(TRANSACTION_ID, any()) } returns null
        every { sagaRepository.findById(TRANSACTION_ID) } returns null

        val result = guard.advance(StockPending(TRANSACTION_ID, ORDER_ID), NOW)

        assertThat(result).isEqualTo(SagaStepResult.Discarded(TRANSACTION_ID, currentStep = null, seq = null))
    }
}
