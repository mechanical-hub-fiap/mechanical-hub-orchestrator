package com.fiap.mechanicalhub.orchestrator.application.usecases

import com.fiap.mechanicalhub.orchestrator.application.ports.SagaRepository
import com.fiap.mechanicalhub.orchestrator.application.ports.TransactionHistoryEntry
import com.fiap.mechanicalhub.orchestrator.application.ports.TransactionHistoryQueryPort
import com.fiap.mechanicalhub.orchestrator.domain.model.Saga
import java.util.UUID

/** Transacao da saga com a trilha completa. */
data class TransactionView(
    val saga: Saga,
    val history: List<TransactionHistoryEntry>,
)

/** Consulta operacional: `transaction_register` + `transaction_history` de uma ordem. */
class GetTransactionUseCase(
    private val sagaRepository: SagaRepository,
    private val historyQuery: TransactionHistoryQueryPort,
) {
    /** `null` quando nao ha saga para a ordem. */
    fun execute(orderId: UUID): TransactionView? =
        sagaRepository.findByOrderId(orderId)?.let { saga ->
            TransactionView(saga, historyQuery.findByOrderId(orderId))
        }
}
