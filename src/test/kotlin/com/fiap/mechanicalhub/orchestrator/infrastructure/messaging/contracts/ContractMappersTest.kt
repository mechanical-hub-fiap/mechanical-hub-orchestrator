package com.fiap.mechanicalhub.orchestrator.infrastructure.messaging.contracts

import com.fiap.mechanicalhub.orchestrator.domain.enums.PaymentFailureReasonEnum
import com.fiap.mechanicalhub.orchestrator.domain.model.BudgetGenerated
import com.fiap.mechanicalhub.orchestrator.domain.model.CancelOrderCommand
import com.fiap.mechanicalhub.orchestrator.domain.model.ClearStockPendingCommand
import com.fiap.mechanicalhub.orchestrator.domain.model.ConsumeStockCommand
import com.fiap.mechanicalhub.orchestrator.domain.model.DiagnosisCompleted
import com.fiap.mechanicalhub.orchestrator.domain.model.GenerateBudgetCommand
import com.fiap.mechanicalhub.orchestrator.domain.model.GeneratePaymentLinkCommand
import com.fiap.mechanicalhub.orchestrator.domain.model.MarkStockPendingCommand
import com.fiap.mechanicalhub.orchestrator.domain.model.OrderApproved
import com.fiap.mechanicalhub.orchestrator.domain.model.OrderCanceled
import com.fiap.mechanicalhub.orchestrator.domain.model.OrderFinished
import com.fiap.mechanicalhub.orchestrator.domain.model.OrderRejected
import com.fiap.mechanicalhub.orchestrator.domain.model.PaymentConfirmed
import com.fiap.mechanicalhub.orchestrator.domain.model.PaymentFailed
import com.fiap.mechanicalhub.orchestrator.domain.model.PaymentLinkGenerated
import com.fiap.mechanicalhub.orchestrator.domain.model.ReleaseOrderCommand
import com.fiap.mechanicalhub.orchestrator.domain.model.ReserveStockCommand
import com.fiap.mechanicalhub.orchestrator.domain.model.RestoreStockCommand
import com.fiap.mechanicalhub.orchestrator.domain.model.SagaCommand
import com.fiap.mechanicalhub.orchestrator.domain.model.SagaEvent
import com.fiap.mechanicalhub.orchestrator.domain.model.SendApprovalCommand
import com.fiap.mechanicalhub.orchestrator.domain.model.SendPaymentLinkCommand
import com.fiap.mechanicalhub.orchestrator.domain.model.StockConsumed
import com.fiap.mechanicalhub.orchestrator.domain.model.StockPending
import com.fiap.mechanicalhub.orchestrator.domain.model.StockReserved
import com.fiap.mechanicalhub.orchestrator.domain.model.StockRestored
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.BUDGET
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.NOW
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.ORDER_ID
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.PAYMENT_EXPIRES_AT
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.PAYMENT_LINK
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.REPAIRS
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.RESERVED_MATERIALS
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures.TRANSACTION_ID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import tools.jackson.core.JacksonException
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.util.UUID

class ContractMappersTest {
    private val jsonMapper: JsonMapper =
        JsonMapper
            .builder()
            .addModule(KotlinModule.Builder().build())
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build()

    @ParameterizedTest(name = "{0}")
    @MethodSource("events")
    fun `given an event JSON when reading the contract then it maps to the domain event`(
        type: String,
        payload: String,
        expected: SagaEvent,
    ) {
        val body = envelope(type, payload)

        val event = jsonMapper.readValue(body, EventMessage::class.java).toDomain()

        assertThat(event).isEqualTo(expected)
    }

    @Test
    fun `given diagnosisCompleted without transactionId when reading the contract then it keeps the envelope fields`() {
        val body =
            """{"eventId":"$EVENT_ID","orderId":"$ORDER_ID","type":"diagnosisCompleted","version":1,"timestamp":"$NOW","repairs":[]}"""

        val message = jsonMapper.readValue(body, EventMessage::class.java)

        assertThat(message).isEqualTo(DiagnosisCompletedMessage(EVENT_ID, ORDER_ID, 1, NOW, emptyList()))
    }

    @Test
    fun `given an unknown event type when reading the contract then it violates the contract`() {
        val body = envelope("somethingElse", "")

        val reading = { jsonMapper.readValue(body, EventMessage::class.java) }

        assertThatThrownBy { reading() }.isInstanceOf(JacksonException::class.java)
    }

    @Test
    fun `given a response without transactionId when reading the contract then it violates the contract`() {
        val body = """{"eventId":"$EVENT_ID","orderId":"$ORDER_ID","type":"orderApproved","version":1,"timestamp":"$NOW"}"""

        val reading = { jsonMapper.readValue(body, EventMessage::class.java) }

        assertThatThrownBy { reading() }.isInstanceOf(JacksonException::class.java)
    }

    @Test
    fun `given paymentFailed with an unknown reason when mapping to the domain then it violates the contract`() {
        val message = jsonMapper.readValue(envelope("paymentFailed", ""","reason":"OTHER""""), EventMessage::class.java)

        val mapping = { message.toDomain() }

        assertThatThrownBy { mapping() }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("commands")
    fun `given a saga command when writing the contract then the JSON has envelope, idempotency key and payload`(
        command: SagaCommand,
        type: String,
        expectedPayload: Map<String, Any>,
    ) {
        val message = command.toMessage(EVENT_ID, NOW)

        val json = jsonMapper.readValue(jsonMapper.writeValueAsString(message), Map::class.java)

        assertThat(json)
            .isEqualTo(
                mapOf(
                    "eventId" to EVENT_ID.toString(),
                    "transactionId" to TRANSACTION_ID.toString(),
                    "orderId" to ORDER_ID.toString(),
                    "type" to type,
                    "version" to CONTRACT_VERSION,
                    "timestamp" to NOW.toString(),
                    "idempotencyKey" to "$TRANSACTION_ID:${command.type.step}",
                ) + expectedPayload,
            )
    }

    @Test
    fun `given a command JSON when reading the contract then it round trips to the same message`() {
        val message = SendApprovalCommand(TRANSACTION_ID, ORDER_ID, 3, BUDGET.budgetId, BUDGET.orderCost).toMessage(EVENT_ID, NOW)

        val read = jsonMapper.readValue(jsonMapper.writeValueAsString(message), CommandMessage::class.java)

        assertThat(read).isEqualTo(message)
    }

    companion object {
        private val EVENT_ID: UUID = UUID.fromString("11111111-2222-4333-8444-555555555555")

        private fun envelope(
            type: String,
            payload: String,
        ) = """{"eventId":"$EVENT_ID","transactionId":"$TRANSACTION_ID","orderId":"$ORDER_ID",""" +
            """"type":"$type","version":1,"timestamp":"$NOW"$payload}"""

        private val REPAIRS_JSON = REPAIRS.joinToString(",", "[", "]") { """{"repairId":"${it.repairId}"}""" }
        private val MATERIALS_JSON =
            RESERVED_MATERIALS.joinToString(",", "[", "]") { """{"materialId":"${it.materialId}","quantity":${it.quantity}}""" }

        private val REPAIRS_MAPS = REPAIRS.map { mapOf("repairId" to it.repairId.toString()) }
        private val MATERIALS_MAPS = RESERVED_MATERIALS.map { mapOf("materialId" to it.materialId.toString(), "quantity" to it.quantity) }

        @JvmStatic
        fun events(): List<Arguments> =
            listOf(
                Arguments.of("diagnosisCompleted", ""","repairs":$REPAIRS_JSON""", DiagnosisCompleted(ORDER_ID, REPAIRS)),
                Arguments.of("stockPending", "", StockPending(TRANSACTION_ID, ORDER_ID)),
                Arguments.of(
                    "stockReserved",
                    ""","reservedMaterials":$MATERIALS_JSON""",
                    StockReserved(TRANSACTION_ID, ORDER_ID, RESERVED_MATERIALS),
                ),
                Arguments.of(
                    "budgetGenerated",
                    ""","budgetId":"${BUDGET.budgetId}","orderCost":350.00""",
                    BudgetGenerated(TRANSACTION_ID, ORDER_ID, BUDGET),
                ),
                Arguments.of("orderApproved", "", OrderApproved(TRANSACTION_ID, ORDER_ID)),
                Arguments.of("orderRejected", "", OrderRejected(TRANSACTION_ID, ORDER_ID)),
                Arguments.of(
                    "paymentLinkGenerated",
                    ""","paymentLink":"$PAYMENT_LINK","expiresAt":"$PAYMENT_EXPIRES_AT"""",
                    PaymentLinkGenerated(TRANSACTION_ID, ORDER_ID, PAYMENT_LINK, PAYMENT_EXPIRES_AT),
                ),
                Arguments.of("paymentConfirmed", ""","paymentId":"pay-123"""", PaymentConfirmed(TRANSACTION_ID, ORDER_ID, "pay-123")),
                Arguments.of(
                    "paymentFailed",
                    ""","reason":"FAILED"""",
                    PaymentFailed(TRANSACTION_ID, ORDER_ID, PaymentFailureReasonEnum.FAILED),
                ),
                Arguments.of("orderCanceled", "", OrderCanceled(TRANSACTION_ID, ORDER_ID)),
                Arguments.of("orderFinished", "", OrderFinished(TRANSACTION_ID, ORDER_ID)),
                Arguments.of("stockConsumed", "", StockConsumed(TRANSACTION_ID, ORDER_ID)),
                Arguments.of("stockRestored", "", StockRestored(TRANSACTION_ID, ORDER_ID)),
            )

        @JvmStatic
        fun commands(): List<Arguments> =
            listOf(
                Arguments.of(ReserveStockCommand(TRANSACTION_ID, ORDER_ID, 1, REPAIRS), "reserveStock", mapOf("repairs" to REPAIRS_MAPS)),
                Arguments.of(
                    GenerateBudgetCommand(TRANSACTION_ID, ORDER_ID, 2, REPAIRS, RESERVED_MATERIALS),
                    "generateBudget",
                    mapOf("repairs" to REPAIRS_MAPS, "reservedMaterials" to MATERIALS_MAPS),
                ),
                Arguments.of(
                    GeneratePaymentLinkCommand(TRANSACTION_ID, ORDER_ID, 4, BUDGET.budgetId, BUDGET.orderCost),
                    "generatePaymentLink",
                    mapOf("budgetId" to BUDGET.budgetId.toString(), "orderCost" to BUDGET.orderCost),
                ),
                Arguments.of(
                    ConsumeStockCommand(TRANSACTION_ID, ORDER_ID, 7, RESERVED_MATERIALS),
                    "consumeStock",
                    mapOf("reservedMaterials" to MATERIALS_MAPS),
                ),
                Arguments.of(
                    RestoreStockCommand(TRANSACTION_ID, ORDER_ID, 4, RESERVED_MATERIALS),
                    "restoreStock",
                    mapOf("reservedMaterials" to MATERIALS_MAPS),
                ),
                Arguments.of(
                    SendApprovalCommand(TRANSACTION_ID, ORDER_ID, 3, BUDGET.budgetId, BUDGET.orderCost),
                    "sendApproval",
                    mapOf("budgetId" to BUDGET.budgetId.toString(), "orderCost" to BUDGET.orderCost),
                ),
                Arguments.of(MarkStockPendingCommand(TRANSACTION_ID, ORDER_ID, 2), "markStockPending", emptyMap<String, Any>()),
                Arguments.of(ClearStockPendingCommand(TRANSACTION_ID, ORDER_ID, 3), "clearStockPending", emptyMap<String, Any>()),
                Arguments.of(
                    SendPaymentLinkCommand(TRANSACTION_ID, ORDER_ID, 5, PAYMENT_LINK),
                    "sendPaymentLink",
                    mapOf("paymentLink" to PAYMENT_LINK),
                ),
                Arguments.of(ReleaseOrderCommand(TRANSACTION_ID, ORDER_ID, 6), "releaseOrder", emptyMap<String, Any>()),
                Arguments.of(
                    CancelOrderCommand(TRANSACTION_ID, ORDER_ID, 6),
                    "cancelOrder",
                    mapOf("reason" to "PAYMENT_NOT_CONFIRMED"),
                ),
            )
    }
}
