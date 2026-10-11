package com.fiap.mechanicalhub.orchestrator

import com.fiap.mechanicalhub.orchestrator.application.usecases.SagaEventHandler
import com.fiap.mechanicalhub.orchestrator.application.usecases.SagaStepGuard
import com.fiap.mechanicalhub.orchestrator.application.usecases.SagaStepResult
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaEventTypeEnum
import com.fiap.mechanicalhub.orchestrator.domain.model.SagaEvent
import com.fiap.mechanicalhub.orchestrator.domain.model.SagaResponseEvent
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import java.time.Instant

/**
 * Handler de teste de `stockPending` (so guarda de passo) para exercitar o
 * pipeline do consumidor (Etapa 4) antes dos handlers reais. Remover quando a
 * Etapa 5 registrar o handler real de `stockPending` (o dispatcher recusa dois
 * handlers para o mesmo tipo).
 */
@TestConfiguration(proxyBeanMethods = false)
class TestSagaEventHandlersConfiguration {
    @Bean
    fun testStockPendingHandler(guard: SagaStepGuard): SagaEventHandler =
        object : SagaEventHandler {
            override val eventType = SagaEventTypeEnum.STOCK_PENDING

            override fun handle(
                event: SagaEvent,
                now: Instant,
            ): SagaStepResult = guard.advance(event as SagaResponseEvent, now)
        }
}
