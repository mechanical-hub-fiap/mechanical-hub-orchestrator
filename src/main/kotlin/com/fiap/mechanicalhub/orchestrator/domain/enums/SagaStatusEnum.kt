package com.fiap.mechanicalhub.orchestrator.domain.enums

/** Status da transacao (saga_transaction_status.md). `terminal` indica saga encerrada (`finishedAt` preenchido). */
enum class SagaStatusEnum(
    val terminal: Boolean,
) {
    STARTED(terminal = false),
    COMPLETED(terminal = true),
    COMPENSATING(terminal = false),
    COMPENSATED(terminal = true),
}
