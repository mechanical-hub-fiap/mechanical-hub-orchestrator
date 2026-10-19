package com.fiap.mechanicalhub.orchestrator.infrastructure.config

import com.fiap.mechanicalhub.orchestrator.application.usecases.GuardedSagaEventHandler
import com.fiap.mechanicalhub.orchestrator.application.usecases.SagaEventHandler
import com.fiap.mechanicalhub.orchestrator.application.usecases.SagaStepGuard
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaEventTypeEnum
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Handlers do caminho feliz e da pendencia de estoque (todos com guarda de
 * passo). O `diagnosisCompleted`, que cria a saga, e o `DiagnosisCompletedHandler`.
 */
@Configuration(proxyBeanMethods = false)
class SagaHandlersConfig {
    @Bean
    fun stockPendingHandler(guard: SagaStepGuard): SagaEventHandler = GuardedSagaEventHandler(SagaEventTypeEnum.STOCK_PENDING, guard)

    @Bean
    fun stockReservedHandler(guard: SagaStepGuard): SagaEventHandler = GuardedSagaEventHandler(SagaEventTypeEnum.STOCK_RESERVED, guard)

    @Bean
    fun budgetGeneratedHandler(guard: SagaStepGuard): SagaEventHandler =
        GuardedSagaEventHandler(SagaEventTypeEnum.BUDGET_GENERATED, guard)

    @Bean
    fun orderApprovedHandler(guard: SagaStepGuard): SagaEventHandler = GuardedSagaEventHandler(SagaEventTypeEnum.ORDER_APPROVED, guard)

    @Bean
    fun paymentLinkGeneratedHandler(guard: SagaStepGuard): SagaEventHandler =
        GuardedSagaEventHandler(SagaEventTypeEnum.PAYMENT_LINK_GENERATED, guard)

    @Bean
    fun paymentConfirmedHandler(guard: SagaStepGuard): SagaEventHandler =
        GuardedSagaEventHandler(SagaEventTypeEnum.PAYMENT_CONFIRMED, guard)

    @Bean
    fun orderFinishedHandler(guard: SagaStepGuard): SagaEventHandler = GuardedSagaEventHandler(SagaEventTypeEnum.ORDER_FINISHED, guard)

    @Bean
    fun stockConsumedHandler(guard: SagaStepGuard): SagaEventHandler = GuardedSagaEventHandler(SagaEventTypeEnum.STOCK_CONSUMED, guard)
}
