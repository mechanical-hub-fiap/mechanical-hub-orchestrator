package com.fiap.mechanicalhub.orchestrator.application.usecases

import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaEventTypeEnum
import com.fiap.mechanicalhub.orchestrator.domain.model.SagaEvent
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class SagaEventDispatcherTest {
    @Test
    fun `given a handler for the type when dispatching then it returns that handler`() {
        val handler = handlerFor(SagaEventTypeEnum.STOCK_PENDING)

        val resolved = SagaEventDispatcher(listOf(handler)).handlerFor(SagaEventTypeEnum.STOCK_PENDING)

        assertThat(resolved).isSameAs(handler)
    }

    @Test
    fun `given no handler for the type when dispatching then it fails as unsupported`() {
        val dispatcher = SagaEventDispatcher(listOf(handlerFor(SagaEventTypeEnum.STOCK_PENDING)))

        val dispatching = { dispatcher.handlerFor(SagaEventTypeEnum.ORDER_APPROVED) }

        assertThatThrownBy { dispatching() }.isInstanceOf(UnsupportedSagaEventException::class.java)
    }

    @Test
    fun `given two handlers for the same type when creating the dispatcher then it is rejected`() {
        val handlers = listOf(handlerFor(SagaEventTypeEnum.STOCK_PENDING), handlerFor(SagaEventTypeEnum.STOCK_PENDING))

        val creating = { SagaEventDispatcher(handlers) }

        assertThatThrownBy { creating() }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun handlerFor(type: SagaEventTypeEnum) =
        object : SagaEventHandler {
            override val eventType = type

            override fun handle(
                event: SagaEvent,
                now: Instant,
            ): SagaStepResult = SagaStepResult.Discarded(null, null, null)
        }
}
