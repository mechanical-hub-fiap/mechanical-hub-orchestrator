package com.fiap.mechanicalhub.orchestrator.infrastructure.messaging.outbox

import com.fiap.mechanicalhub.orchestrator.application.usecases.RelayOutboxUseCase
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor
import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.core.LockProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

/**
 * Dispara o relay a cada 1 s. A lideranca e um lease do ShedLock: so a instancia
 * que obtem o lock publica; as demais pulam o ciclo.
 */
@Component
@ConditionalOnProperty(prefix = "mechanical-hub.orchestrator.relay", name = ["enabled"], matchIfMissing = true)
class OutboxRelayScheduler(
    private val relay: RelayOutboxUseCase,
    lockProvider: LockProvider,
    private val clock: Clock,
) {
    private val lockExecutor = DefaultLockingTaskExecutor(lockProvider)

    @Scheduled(fixedDelayString = "\${mechanical-hub.orchestrator.relay.interval:1s}")
    fun run() {
        lockExecutor.executeWithLock(
            Runnable { relay.relayBatch() },
            LockConfiguration(clock.instant(), LOCK_NAME, LOCK_AT_MOST_FOR, Duration.ZERO),
        )
    }

    private companion object {
        const val LOCK_NAME = "outboxRelay"
        val LOCK_AT_MOST_FOR: Duration = Duration.ofSeconds(30)
    }
}
