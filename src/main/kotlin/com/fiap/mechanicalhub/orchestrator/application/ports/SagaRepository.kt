package com.fiap.mechanicalhub.orchestrator.application.ports

import com.fiap.mechanicalhub.orchestrator.domain.model.Saga
import com.fiap.mechanicalhub.orchestrator.domain.model.SagaChange
import java.util.UUID

/** `transaction_register`: participa da transacao do caso de uso. */
interface SagaRepository {
    fun findById(transactionId: UUID): Saga?

    fun findByOrderId(orderId: UUID): Saga?

    /** `insertOne` da saga nova; `orderId` unico (`E11000` se ja existir). */
    fun insert(saga: Saga)

    /**
     * Guarda de passo + atualizacao atomica (`findOneAndUpdate` filtrando
     * `currentStep`, com `$inc: { seq: n }` e `returnDocument: after`).
     * `null` quando a saga nao esta no passo esperado.
     */
    fun applyChange(
        transactionId: UUID,
        change: SagaChange,
    ): Saga?
}
