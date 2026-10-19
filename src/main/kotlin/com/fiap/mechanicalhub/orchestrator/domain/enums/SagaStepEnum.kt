package com.fiap.mechanicalhub.orchestrator.domain.enums

/** Passo atual da saga (`currentStep`, saga_transaction_status.md). Saga encerrada fica sem passo (`null`). */
enum class SagaStepEnum {
    RESERVE_STOCK,
    AWAITING_STOCK,
    GENERATE_BUDGET,
    WAITING_APPROVAL,
    AWAITING_PAYMENT,
    AWAITING_EXECUTION,
    CONSUME_STOCK,
    CANCEL_ORDER,
    RESTORE_STOCK,
}
