package com.fiap.mechanicalhub.orchestrator.application.ports

import java.util.UUID

/** Leitura da trilha da saga (`transaction_history`) para a consulta operacional. */
interface TransactionHistoryQueryPort {
    /** Linhas da ordem, por `seq` crescente (linhas sem `seq` ao final, por `receivedAt`). */
    fun findByOrderId(orderId: UUID): List<TransactionHistoryEntry>
}
