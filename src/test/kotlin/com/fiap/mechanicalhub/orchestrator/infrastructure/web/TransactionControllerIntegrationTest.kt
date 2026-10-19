package com.fiap.mechanicalhub.orchestrator.infrastructure.web

import com.fiap.mechanicalhub.orchestrator.SagaIntegrationTest
import com.fiap.mechanicalhub.orchestrator.application.ports.TransactionHistoryEntry
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStepEnum
import com.fiap.mechanicalhub.orchestrator.domain.enums.TransactionOutcomeEnum
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.mappers.toDocument
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Instant
import java.util.UUID

@AutoConfigureMockMvc
class TransactionControllerIntegrationTest : SagaIntegrationTest() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `given a saga with history when querying by order then it returns the transaction and the history ordered by seq`() {
        val saga = givenSaga(SagaStepEnum.GENERATE_BUDGET, seq = 3)
        append(saga.transactionId, saga.orderId, 2, "stockReserved")
        append(saga.transactionId, saga.orderId, 1, "diagnosisCompleted")

        mockMvc
            .get("/transaction/${saga.orderId}")
            .andExpect {
                status { isOk() }
                jsonPath("$.transaction.transactionId") { value(saga.transactionId.toString()) }
                jsonPath("$.transaction.currentStep") { value("GENERATE_BUDGET") }
                jsonPath("$.transaction.seq") { value(3) }
                jsonPath("$.history.length()") { value(2) }
                jsonPath("$.history[0].messageType") { value("diagnosisCompleted") }
                jsonPath("$.history[1].messageType") { value("stockReserved") }
            }
    }

    @Test
    fun `given an unknown order when querying then it returns 404`() {
        mockMvc.get("/transaction/${UUID.randomUUID()}").andExpect { status { isNotFound() } }
    }

    @Test
    fun `given an invalid order id when querying then it returns 400`() {
        mockMvc.get("/transaction/not-a-uuid").andExpect { status { isBadRequest() } }
    }

    private fun append(
        transactionId: UUID,
        orderId: UUID,
        seq: Long,
        type: String,
    ) {
        mongoTemplate.insert(
            TransactionHistoryEntry(
                transactionId,
                orderId,
                seq,
                type,
                UUID.randomUUID().toString(),
                null,
                SagaStepEnum.RESERVE_STOCK,
                TransactionOutcomeEnum.APPLIED,
                Instant.now(),
            ).toDocument(),
        )
    }
}
