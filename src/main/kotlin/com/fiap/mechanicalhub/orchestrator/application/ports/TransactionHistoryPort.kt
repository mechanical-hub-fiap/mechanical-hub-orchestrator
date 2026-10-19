package com.fiap.mechanicalhub.orchestrator.application.ports

import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStepEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.TransactionOutcomeEnum
import java.time.Instant
import java.util.UUID

/** Linha append-only de `transaction_history` (faz o papel da `inbox_events`). */
data class TransactionHistoryEntry(
    val transactionId: UUID?,
    val orderId: UUID,
    val seq: Long?,
    val messageType: String,
    val messageKey: String,
    val stepBefore: SagaStepEnum?,
    val stepAfter: SagaStepEnum?,
    val outcome: TransactionOutcomeEnum,
    val receivedAt: Instant,
)

/** Mensagem ja registrada: o indice unico de `messageKey` rejeitou o insert (`E11000`). */
class DuplicateMessageException(
    messageKey: String,
    cause: Throwable? = null,
) : RuntimeException("Mensagem $messageKey ja processada", cause)

/** `transaction_history`: deduplicacao por `messageKey = eventId`; participa da transacao do caso de uso. */
interface TransactionHistoryPort {
    fun existsByMessageKey(messageKey: String): Boolean

    /** Lanca [DuplicateMessageException] se o `messageKey` ja existir. */
    fun append(entry: TransactionHistoryEntry)
}
