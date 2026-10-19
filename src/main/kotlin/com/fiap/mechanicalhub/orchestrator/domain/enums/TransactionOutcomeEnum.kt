package com.fiap.mechanicalhub.orchestrator.domain.enums

/** Resultado do processamento de uma mensagem, gravado em `transaction_history.outcome`. */
enum class TransactionOutcomeEnum {
    APPLIED,
    DISCARDED_STEP_GUARD,
}
