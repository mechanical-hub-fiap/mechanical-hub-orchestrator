package com.fiap.mechanicalhub.orchestrator.domain.exceptions

import java.util.UUID

/** Base das excecoes de dominio da saga. */
sealed class SagaDomainException(
    message: String,
) : RuntimeException(message)

/** A saga nao tem um dado acumulado que o comando do passo exige (ex.: `budget` ausente no `sendApproval`). */
class SagaInconsistentStateException(
    transactionId: UUID,
    detail: String,
) : SagaDomainException("Saga $transactionId em estado inconsistente: $detail")

/** Transicao usada com um evento de outro tipo ou com um passo que nao e o esperado. */
class SagaTransitionMismatchException(
    detail: String,
) : SagaDomainException(detail)
