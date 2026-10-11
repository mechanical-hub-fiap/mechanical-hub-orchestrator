package com.fiap.mechanicalhub.orchestrator.application.ports

import com.fiap.mechanicalhub.orchestrator.domain.model.SagaCommand
import java.time.Instant

/** `outbox_events`: grava os comandos como `PENDING`, com o `seq` reservado na saga, na transacao do caso de uso. */
interface OutboxPort {
    fun append(
        commands: List<SagaCommand>,
        createdAt: Instant,
    )
}
