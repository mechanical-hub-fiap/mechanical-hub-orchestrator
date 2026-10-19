package com.fiap.mechanicalhub.orchestrator.application.ports

import com.fiap.mechanicalhub.orchestrator.domain.enums.CommandDestinationEnum
import java.time.Instant

/** Comando `PENDING` da `outbox_events`; `payload` e o corpo JSON pronto para a fila. */
data class OutboxMessage(
    val eventId: String,
    val orderId: String,
    val seq: Long,
    val eventType: String,
    val payload: String,
    val attempts: Int,
    val createdAt: Instant,
)

/** Leitura e marcacao da `outbox_events` feitas pelo relay. */
interface OutboxRelayStore {
    /** Os `limit` mais antigos `PENDING`, por `createdAt` e, dentro dele, `seq`. */
    fun findPending(limit: Int): List<OutboxMessage>

    fun markSent(
        eventId: String,
        sentAt: Instant,
    )

    fun markFailed(
        eventId: String,
        error: String,
    )
}

/** Envio de um comando a fila do servico de destino. */
interface CommandPublisher {
    fun publish(
        destination: CommandDestinationEnum,
        message: OutboxMessage,
    )
}
