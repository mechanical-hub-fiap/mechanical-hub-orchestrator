package com.fiap.mechanicalhub.orchestrator.application.usecases

import com.fiap.mechanicalhub.orchestrator.application.ports.CommandPublisher
import com.fiap.mechanicalhub.orchestrator.application.ports.OutboxMessage
import com.fiap.mechanicalhub.orchestrator.application.ports.OutboxRelayStore
import com.fiap.mechanicalhub.orchestrator.domain.enums.CommandDestinationEnum
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RelayOutboxUseCaseTest {
    private val now = Instant.parse("2026-01-01T10:00:00Z")
    private val store = mockk<OutboxRelayStore>(relaxed = true)
    private val publisher = mockk<CommandPublisher>(relaxed = true)
    private val relay = RelayOutboxUseCase(store, publisher, Clock.fixed(now, ZoneOffset.UTC))

    private fun message(
        id: String,
        seq: Long,
        type: String,
        createdAt: Instant = now.minusSeconds(10),
    ) = OutboxMessage(id, "order-1", seq, type, "{}", 0, createdAt)

    @Test
    fun `given pending commands when relaying then they are sent in seq order to the routed queues and marked as sent`() {
        every { store.findPending(any()) } returns
            listOf(message("e2", 2, "generateBudget"), message("e1", 1, "clearStockPending"))

        val sent = relay.relayBatch()

        assertThat(sent).isEqualTo(2)
        verifyOrder {
            publisher.publish(CommandDestinationEnum.ORDERS, match { it.eventId == "e1" })
            store.markSent("e1", now)
            publisher.publish(CommandDestinationEnum.BILLING, match { it.eventId == "e2" })
            store.markSent("e2", now)
        }
    }

    @Test
    fun `given a failure when relaying then the batch stops and the error is recorded`() {
        every { store.findPending(any()) } returns
            listOf(message("e1", 1, "reserveStock"), message("e2", 2, "generateBudget"))
        every { publisher.publish(any(), match { it.eventId == "e1" }) } throws IllegalStateException("sqs down")

        val sent = relay.relayBatch()

        assertThat(sent).isZero()
        verify { store.markFailed("e1", match { it.contains("sqs down") }) }
        verify(exactly = 1) { publisher.publish(any(), any()) }
        verify(exactly = 0) { store.markSent(any(), any()) }
    }

    @Test
    fun `given an unknown command type when relaying then it is recorded as failed and nothing is published`() {
        every { store.findPending(any()) } returns listOf(message("e1", 1, "unknown"))

        relay.relayBatch()

        verify { store.markFailed("e1", any()) }
        verify(exactly = 0) { publisher.publish(any(), any()) }
    }
}
