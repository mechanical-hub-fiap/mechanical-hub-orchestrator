package com.fiap.mechanicalhub.orchestrator.infrastructure.messaging.outbox

import com.fiap.mechanicalhub.orchestrator.SagaIntegrationTest
import com.fiap.mechanicalhub.orchestrator.application.usecases.RelayOutboxUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.javacrumbs.shedlock.core.LockProvider
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.until
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Clock
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Duas instancias do relay disputando o mesmo lease no MongoDB: so a lider publica. */
class OutboxRelaySchedulerLeadershipIntegrationTest : SagaIntegrationTest() {
    @Autowired
    private lateinit var lockProvider: LockProvider

    @Autowired
    private lateinit var clock: Clock

    @Test
    fun `given two instances when the leader holds the lease then only the leader relays and the other waits for the next cycle`() {
        val insideLeaderCycle = CountDownLatch(1)
        val releaseLeader = CountDownLatch(1)
        val leaderRelay = mockk<RelayOutboxUseCase>()
        every { leaderRelay.relayBatch(any()) } answers {
            insideLeaderCycle.countDown()
            releaseLeader.await(LEASE_WAIT_SECONDS, TimeUnit.SECONDS)
            0
        }
        val followerRuns = AtomicInteger()
        val followerRelay = mockk<RelayOutboxUseCase>()
        every { followerRelay.relayBatch(any()) } answers {
            followerRuns.incrementAndGet()
            0
        }
        val leader = OutboxRelayScheduler(leaderRelay, lockProvider, clock)
        val follower = OutboxRelayScheduler(followerRelay, lockProvider, clock)
        val executor = Executors.newSingleThreadExecutor()
        val leaderFinished = AtomicBoolean(false)

        try {
            executor.submit {
                // O scheduler real do contexto tambem disputa o lease; tenta ate assumir a lideranca.
                while (insideLeaderCycle.count > 0) {
                    leader.run()
                    Thread.sleep(RETRY_MILLIS)
                }
                leaderFinished.set(true)
            }
            assertThat(insideLeaderCycle.await(LEASE_WAIT_SECONDS, TimeUnit.SECONDS)).isTrue()

            follower.run()

            verify(exactly = 0) { followerRelay.relayBatch(any()) }
            releaseLeader.countDown()
            await atMost Duration.ofSeconds(LEASE_WAIT_SECONDS) until { leaderFinished.get() }

            // O scheduler real do contexto tambem disputa o lease; tenta ate assumir a lideranca.
            await atMost Duration.ofSeconds(LEASE_WAIT_SECONDS) until {
                follower.run()
                followerRuns.get() > 0
            }
        } finally {
            releaseLeader.countDown()
            executor.shutdownNow()
        }
    }

    private companion object {
        const val LEASE_WAIT_SECONDS = 10L
        const val RETRY_MILLIS = 50L
    }
}
