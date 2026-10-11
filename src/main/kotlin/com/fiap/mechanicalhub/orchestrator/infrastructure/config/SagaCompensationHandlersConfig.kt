package com.fiap.mechanicalhub.orchestrator.infrastructure.config

import com.fiap.mechanicalhub.orchestrator.application.usecases.GuardedSagaEventHandler
import com.fiap.mechanicalhub.orchestrator.application.usecases.SagaEventHandler
import com.fiap.mechanicalhub.orchestrator.application.usecases.SagaStepGuard
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaEventTypeEnum
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Handlers das compensacoes: orcamento recusado e pagamento nao confirmado. */
@Configuration(proxyBeanMethods = false)
class SagaCompensationHandlersConfig {
    @Bean
    fun orderRejectedHandler(guard: SagaStepGuard): SagaEventHandler = GuardedSagaEventHandler(SagaEventTypeEnum.ORDER_REJECTED, guard)

    @Bean
    fun paymentFailedHandler(guard: SagaStepGuard): SagaEventHandler = GuardedSagaEventHandler(SagaEventTypeEnum.PAYMENT_FAILED, guard)

    @Bean
    fun orderCanceledHandler(guard: SagaStepGuard): SagaEventHandler = GuardedSagaEventHandler(SagaEventTypeEnum.ORDER_CANCELED, guard)

    @Bean
    fun stockRestoredHandler(guard: SagaStepGuard): SagaEventHandler = GuardedSagaEventHandler(SagaEventTypeEnum.STOCK_RESTORED, guard)
}
