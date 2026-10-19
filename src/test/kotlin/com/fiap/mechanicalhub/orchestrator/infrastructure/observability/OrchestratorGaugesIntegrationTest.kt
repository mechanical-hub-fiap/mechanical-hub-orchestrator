package com.fiap.mechanicalhub.orchestrator.infrastructure.observability

import com.fiap.mechanicalhub.orchestrator.SagaIntegrationTest
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStepEnum
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.documents.OutboxEventDocument
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.documents.OutboxStatusEnum
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.Instant
import java.util.UUID

class OrchestratorGaugesIntegrationTest : SagaIntegrationTest() {
    @Autowired
    private lateinit var registry: MeterRegistry

    @Test
    fun `given a pending outbox row when reading the gauge then it reports the age of the oldest one`() {
        val eventId = UUID.randomUUID().toString()
        mongoTemplate.insert(
            OutboxEventDocument(
                id = eventId,
                orderId = UUID.randomUUID().toString(),
                seq = 1,
                eventType = "reserveStock",
                payload = "{}",
                status = OutboxStatusEnum.PENDING,
                attempts = 0,
                lastError = null,
                createdAt = Instant.now().minusSeconds(600),
                sentAt = null,
            ),
        )

        val age = registry.get("outbox.oldest.pending.age").gauge().value()

        assertThat(age).isGreaterThanOrEqualTo(599.0)
    }

    @Test
    fun `given active sagas when reading the gauge then it counts them by current step`() {
        val before = activeAt(SagaStepEnum.WAITING_APPROVAL)
        givenSaga(SagaStepEnum.WAITING_APPROVAL)

        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            assertThat(activeAt(SagaStepEnum.WAITING_APPROVAL)).isEqualTo(before + 1)
        }
    }

    private fun activeAt(step: SagaStepEnum) =
        registry
            .get("saga.active")
            .tag("current_step", step.name)
            .gauge()
            .value()
}
