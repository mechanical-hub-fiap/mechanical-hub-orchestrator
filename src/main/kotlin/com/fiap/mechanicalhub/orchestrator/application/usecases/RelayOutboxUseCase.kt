package com.fiap.mechanicalhub.orchestrator.application.usecases

import com.fiap.mechanicalhub.orchestrator.application.ports.CommandPublisher
import com.fiap.mechanicalhub.orchestrator.application.ports.OutboxMessage
import com.fiap.mechanicalhub.orchestrator.application.ports.OutboxRelayStore
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaCommandTypeEnum
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock

/**
 * Relay da outbox (TD_finishedOS §0.2): publica o lote de `PENDING` mais antigos.
 * Por `orderId` a ordem e crescente em `seq` e um `seq` so sai depois de o
 * anterior ter sido enviado. Um erro interrompe o lote inteiro, registrando
 * `attempts` e `lastError`; o mesmo comando e retentado no proximo ciclo.
 */
@Service
class RelayOutboxUseCase(
    private val store: OutboxRelayStore,
    private val publisher: CommandPublisher,
    private val clock: Clock,
) {
    /** Retorna quantos comandos foram enviados. */
    fun relayBatch(batchSize: Int = BATCH_SIZE): Int {
        val ordered =
            store
                .findPending(batchSize)
                .sortedWith(compareBy(OutboxMessage::createdAt, OutboxMessage::seq))
        var sent = 0
        for (message in ordered) {
            try {
                val destination =
                    SagaCommandTypeEnum.entries.firstOrNull { it.messageType == message.eventType }?.destination
                        ?: error("Tipo de comando sem fila de destino: ${message.eventType}")
                publisher.publish(destination, message)
                store.markSent(message.eventId, clock.instant())
                sent++
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                log.warn("Falha ao publicar {} (orderId={}, seq={}): {}", message.eventType, message.orderId, message.seq, e.toString())
                store.markFailed(message.eventId, e.toString().take(MAX_ERROR_LENGTH))
                break
            }
        }
        return sent
    }

    companion object {
        const val BATCH_SIZE = 100
        private const val MAX_ERROR_LENGTH = 1_000
        private val log = LoggerFactory.getLogger(RelayOutboxUseCase::class.java)
    }
}
