package com.fiap.mechanicalhub.orchestrator.domain.statemachine

import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaCommandTypeEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaEventTypeEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStatusEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStepEnum

/**
 * Maquina de estados da saga como tabela unica, espelhando a secao 3 da spec
 * (e saga_transaction_status.md). Kotlin puro, sem dependencia do Mongo.
 *
 * A ordem das linhas importa: para o mesmo evento, as candidatas sao tentadas
 * na ordem da tabela. O `stockReserved` tenta primeiro a guarda
 * `AWAITING_STOCK` (n = 2) e depois `RESERVE_STOCK` (n = 1).
 */
object SagaStateMachine {
    val transitions: List<SagaTransition> =
        listOf(
            transition(
                SagaEventTypeEnum.DIAGNOSIS_COMPLETED,
                from = null,
                to = SagaStepEnum.RESERVE_STOCK,
                status = SagaStatusEnum.STARTED,
                SagaCommandTypeEnum.RESERVE_STOCK,
            ),
            transition(
                SagaEventTypeEnum.STOCK_PENDING,
                from = SagaStepEnum.RESERVE_STOCK,
                to = SagaStepEnum.AWAITING_STOCK,
                status = SagaStatusEnum.STARTED,
                SagaCommandTypeEnum.MARK_STOCK_PENDING,
            ),
            transition(
                SagaEventTypeEnum.STOCK_RESERVED,
                from = SagaStepEnum.AWAITING_STOCK,
                to = SagaStepEnum.GENERATE_BUDGET,
                status = SagaStatusEnum.STARTED,
                SagaCommandTypeEnum.CLEAR_STOCK_PENDING,
                SagaCommandTypeEnum.GENERATE_BUDGET,
            ),
            transition(
                SagaEventTypeEnum.STOCK_RESERVED,
                from = SagaStepEnum.RESERVE_STOCK,
                to = SagaStepEnum.GENERATE_BUDGET,
                status = SagaStatusEnum.STARTED,
                SagaCommandTypeEnum.GENERATE_BUDGET,
            ),
            transition(
                SagaEventTypeEnum.BUDGET_GENERATED,
                from = SagaStepEnum.GENERATE_BUDGET,
                to = SagaStepEnum.WAITING_APPROVAL,
                status = SagaStatusEnum.STARTED,
                SagaCommandTypeEnum.SEND_APPROVAL,
            ),
            transition(
                SagaEventTypeEnum.ORDER_APPROVED,
                from = SagaStepEnum.WAITING_APPROVAL,
                to = SagaStepEnum.AWAITING_PAYMENT,
                status = SagaStatusEnum.STARTED,
                SagaCommandTypeEnum.GENERATE_PAYMENT_LINK,
            ),
            transition(
                SagaEventTypeEnum.ORDER_REJECTED,
                from = SagaStepEnum.WAITING_APPROVAL,
                to = SagaStepEnum.RESTORE_STOCK,
                status = SagaStatusEnum.COMPENSATING,
                SagaCommandTypeEnum.RESTORE_STOCK,
            ),
            transition(
                SagaEventTypeEnum.PAYMENT_LINK_GENERATED,
                from = SagaStepEnum.AWAITING_PAYMENT,
                to = SagaStepEnum.AWAITING_PAYMENT,
                status = SagaStatusEnum.STARTED,
                SagaCommandTypeEnum.SEND_PAYMENT_LINK,
            ).copy(requiresPaymentLinkAbsent = true),
            transition(
                SagaEventTypeEnum.PAYMENT_CONFIRMED,
                from = SagaStepEnum.AWAITING_PAYMENT,
                to = SagaStepEnum.AWAITING_EXECUTION,
                status = SagaStatusEnum.STARTED,
                SagaCommandTypeEnum.RELEASE_ORDER,
            ),
            transition(
                SagaEventTypeEnum.PAYMENT_FAILED,
                from = SagaStepEnum.AWAITING_PAYMENT,
                to = SagaStepEnum.CANCEL_ORDER,
                status = SagaStatusEnum.COMPENSATING,
                SagaCommandTypeEnum.CANCEL_ORDER,
            ),
            transition(
                SagaEventTypeEnum.ORDER_CANCELED,
                from = SagaStepEnum.CANCEL_ORDER,
                to = SagaStepEnum.RESTORE_STOCK,
                status = SagaStatusEnum.COMPENSATING,
                SagaCommandTypeEnum.RESTORE_STOCK,
            ),
            transition(
                SagaEventTypeEnum.ORDER_FINISHED,
                from = SagaStepEnum.AWAITING_EXECUTION,
                to = SagaStepEnum.CONSUME_STOCK,
                status = SagaStatusEnum.STARTED,
                SagaCommandTypeEnum.CONSUME_STOCK,
            ),
            transition(
                SagaEventTypeEnum.STOCK_CONSUMED,
                from = SagaStepEnum.CONSUME_STOCK,
                to = null,
                status = SagaStatusEnum.COMPLETED,
            ),
            transition(
                SagaEventTypeEnum.STOCK_RESTORED,
                from = SagaStepEnum.RESTORE_STOCK,
                to = null,
                status = SagaStatusEnum.COMPENSATED,
            ),
        )

    /** Transicao que cria a saga (`diagnosisCompleted`). */
    val creation: SagaTransition = transitions.single(SagaTransition::createsSaga)

    /** Guardas candidatas para o evento, na ordem em que devem ser tentadas. Vazio para `diagnosisCompleted`. */
    fun candidatesFor(eventType: SagaEventTypeEnum): List<SagaTransition> =
        transitions.filter { it.eventType == eventType && !it.createsSaga }

    /** Transicao aplicavel ao evento com a saga no passo informado; `null` = descarte pela guarda de passo. */
    fun transitionFor(
        eventType: SagaEventTypeEnum,
        currentStep: SagaStepEnum?,
    ): SagaTransition? = candidatesFor(eventType).firstOrNull { it.expectedStep == currentStep }

    private fun transition(
        eventType: SagaEventTypeEnum,
        from: SagaStepEnum?,
        to: SagaStepEnum?,
        status: SagaStatusEnum,
        vararg commands: SagaCommandTypeEnum,
    ) = SagaTransition(
        eventType = eventType,
        expectedStep = from,
        nextStep = to,
        nextStatus = status,
        commands = commands.toList(),
    )
}
