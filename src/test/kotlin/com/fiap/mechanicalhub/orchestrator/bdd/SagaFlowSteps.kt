package com.fiap.mechanicalhub.orchestrator.bdd

import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStatusEnum
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.documents.TransactionHistoryDocument
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.documents.TransactionRegisterDocument
import io.awspring.cloud.sqs.operations.SqsTemplate
import io.cucumber.datatable.DataTable
import io.cucumber.java.pt.Dado
import io.cucumber.java.pt.E
import io.cucumber.java.pt.Então
import io.cucumber.java.pt.Quando
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.UUID

/**
 * Passos do BDD: orders, stock e billing sao simulados pelas mensagens que publicariam
 * em `mechanical-hub-orchestrator-events.fifo`; os comandos publicados pelo relay sao
 * lidos das tres filas de comandos e confirmados (apagados) como os servicos fariam.
 */
class SagaFlowSteps {
    @Autowired
    private lateinit var sqsTemplate: SqsTemplate

    @Autowired
    private lateinit var sqsAsyncClient: SqsAsyncClient

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    private lateinit var orderId: UUID
    private lateinit var lastEventId: UUID
    private lateinit var lastEventType: String
    private val commands = mutableListOf<Pair<String, String>>()

    @Dado("uma ordem com diagnóstico concluído")
    fun anOrderWithCompletedDiagnosis() {
        orderId = UUID.randomUUID()
        send("diagnosisCompleted", UUID.randomUUID())
        awaitSaga { assertThat(it).isNotNull }
    }

    @Quando("o serviço {string} publica {string}")
    fun serviceSends(
        service: String,
        type: String,
    ) {
        assertThat(PUBLISHER_BY_TYPE[type]).describedAs("publicador de $type").isEqualTo(service)
        send(type, UUID.randomUUID())
    }

    @Quando("o serviço {string} publica {string} novamente com outro eventId")
    fun serviceSendsAgainWithAnotherEventId(
        service: String,
        type: String,
    ) = serviceSends(service, type)

    @E("o último evento é entregue novamente")
    fun lastEventIsDeliveredAgain() = send(lastEventType, lastEventId)

    @Então("a saga termina com status {string} e sem passo atual")
    fun sagaFinishes(status: String) =
        awaitSaga {
            assertThat(it?.status).isEqualTo(SagaStatusEnum.valueOf(status))
            assertThat(it?.currentStep).isNull()
            assertThat(it?.finishedAt).isNotNull()
        }

    @Então("a saga está no passo {string}")
    fun sagaIsAtStep(step: String) = awaitSaga { assertThat(it?.currentStep?.name).isEqualTo(step) }

    @Então("a saga está no passo {string} com status {string}")
    fun sagaIsAtStepWithStatus(
        step: String,
        status: String,
    ) = awaitSaga {
        assertThat(it?.currentStep?.name).isEqualTo(step)
        assertThat(it?.status?.name).isEqualTo(status)
    }

    @Então("o histórico registra {int} mensagem {string} com resultado {string}")
    fun historyRecords(
        count: Int,
        type: String,
        outcome: String,
    ) = await atMost TIMEOUT untilAsserted {
        val history =
            mongoTemplate.find(
                Query(Criteria.where("orderId").`is`(orderId.toString())),
                TransactionHistoryDocument::class.java,
            )
        assertThat(history.count { it.messageType == type && it.outcome.name == outcome }).isEqualTo(count)
    }

    @E("os comandos publicados para a ordem foram, em ordem em cada fila:")
    fun publishedCommands(table: DataTable) {
        val expected = table.asLists().map { it[0]!! to it[1]!! }
        await atMost TIMEOUT untilAsserted {
            drainCommandQueues()
            assertSameCommands(expected)
        }
        // Nenhum comando a mais pode aparecer depois do esperado.
        drainCommandQueues()
        assertSameCommands(expected)
    }

    /** O SQS FIFO so garante a ordem dentro de cada fila (grupo = orderId); entre filas nao ha ordem. */
    private fun assertSameCommands(expected: List<Pair<String, String>>) {
        assertThat(commands).containsExactlyInAnyOrderElementsOf(expected)
        COMMAND_QUEUES.map { it.first }.forEach { service ->
            assertThat(commands.filter { it.second == service }).isEqualTo(expected.filter { it.second == service })
        }
    }

    private fun send(
        type: String,
        eventId: UUID,
    ) {
        lastEventId = eventId
        lastEventType = type
        val body =
            buildMap<String, Any?> {
                put("eventId", eventId.toString())
                put("orderId", orderId.toString())
                put("type", type)
                put("version", 1)
                put("timestamp", "2026-10-11T12:00:00Z")
                if (type != "diagnosisCompleted") put("transactionId", transactionId().toString())
                putAll(payloadOf(type))
            }
        sqsTemplate.send<String> {
            it
                .queue(EVENTS_QUEUE)
                .payload(jsonMapper.writeValueAsString(body))
                .messageGroupId(orderId.toString())
                .messageDeduplicationId(UUID.randomUUID().toString())
        }
    }

    private fun payloadOf(type: String): Map<String, Any?> =
        when (type) {
            "diagnosisCompleted" -> mapOf("repairs" to listOf(mapOf("repairId" to REPAIR_ID)))
            "stockReserved" -> mapOf("reservedMaterials" to listOf(mapOf("materialId" to MATERIAL_ID, "quantity" to 3)))
            "budgetGenerated" -> mapOf("budgetId" to BUDGET_ID, "orderCost" to 350.00)
            "paymentLinkGenerated" -> mapOf("paymentLink" to PAYMENT_LINK, "expiresAt" to "2026-10-12T12:05:00Z")
            "paymentConfirmed" -> mapOf("paymentId" to "pay-1")
            "paymentFailed" -> mapOf("reason" to "EXPIRED")
            else -> emptyMap()
        }

    private fun transactionId(): UUID {
        var found: UUID? = null
        awaitSaga { found = UUID.fromString(it?.id) }
        return found!!
    }

    private fun awaitSaga(assertion: (TransactionRegisterDocument?) -> Unit) {
        await atMost TIMEOUT untilAsserted {
            assertion(
                mongoTemplate.findOne(
                    Query(Criteria.where("orderId").`is`(orderId.toString())),
                    TransactionRegisterDocument::class.java,
                ),
            )
        }
    }

    /** Le e confirma os comandos desta ordem nas tres filas, na ordem de chegada. */
    private fun drainCommandQueues() {
        COMMAND_QUEUES.forEach { (service, queueName) ->
            val queueUrl = sqsAsyncClient.getQueueUrl { it.queueName(queueName) }.join().queueUrl()
            val messages =
                sqsAsyncClient
                    .receiveMessage { it.queueUrl(queueUrl).maxNumberOfMessages(MAX_MESSAGES).waitTimeSeconds(1) }
                    .join()
                    .messages()
            messages.forEach { message ->
                val json = jsonMapper.readTree(message.body())
                if (json["orderId"]?.asString() == orderId.toString()) {
                    commands += json["type"].asString() to service
                }
                sqsAsyncClient.deleteMessage { it.queueUrl(queueUrl).receiptHandle(message.receiptHandle()) }.join()
            }
        }
    }

    private companion object {
        const val EVENTS_QUEUE = "mechanical-hub-orchestrator-events.fifo"
        const val MAX_MESSAGES = 10
        const val REPAIR_ID = "00000000-0000-4000-8000-0000000000a1"
        const val MATERIAL_ID = "00000000-0000-4000-8000-0000000000b1"
        const val BUDGET_ID = "00000000-0000-4000-8000-0000000000c1"
        const val PAYMENT_LINK = "https://www.mercadopago.com.br/checkout/abc"
        val TIMEOUT: Duration = Duration.ofSeconds(30)

        val COMMAND_QUEUES =
            listOf(
                "orders" to "mechanical-hub-orders-commands.fifo",
                "stock" to "mechanical-hub-stock-commands.fifo",
                "billing" to "mechanical-hub-billing-commands.fifo",
            )

        val PUBLISHER_BY_TYPE =
            mapOf(
                "diagnosisCompleted" to "orders",
                "stockPending" to "stock",
                "stockReserved" to "stock",
                "budgetGenerated" to "billing",
                "orderApproved" to "orders",
                "orderRejected" to "orders",
                "paymentLinkGenerated" to "billing",
                "paymentConfirmed" to "billing",
                "paymentFailed" to "billing",
                "orderCanceled" to "orders",
                "orderFinished" to "orders",
                "stockConsumed" to "stock",
                "stockRestored" to "stock",
            )
    }
}
