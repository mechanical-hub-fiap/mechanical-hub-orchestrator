package com.fiap.mechanicalhub.orchestrator.infrastructure.messaging.events

import com.fiap.mechanicalhub.orchestrator.application.ports.DuplicateMessageException
import com.fiap.mechanicalhub.orchestrator.application.usecases.UnsupportedSagaEventException
import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaEventTypeEnum
import com.fiap.mechanicalhub.orchestrator.domain.exceptions.SagaInconsistentStateException
import com.mongodb.MongoException
import com.mongodb.MongoTimeoutException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.UncategorizedMongoDbException
import org.springframework.transaction.TransactionSystemException
import java.util.UUID

class EventErrorClassifierTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("errors")
    fun `given a processing error when classifying then it follows failure handling`(
        description: String,
        error: Throwable,
        expected: EventErrorClass,
    ) {
        val classification = EventErrorClassifier.classify(error)

        assertThat(classification).describedAs(description).isEqualTo(expected)
    }

    companion object {
        @JvmStatic
        fun errors(): List<Arguments> =
            listOf(
                Arguments.of("duplicata no historico", DuplicateMessageException("key", DuplicateKeyException("E11000")), DUPLICATE),
                Arguments.of("TransientTransactionError", labeled(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL), TRANSIENT),
                Arguments.of(
                    "UnknownTransactionCommitResult no commit",
                    TransactionSystemException("commit", labeled(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)),
                    TRANSIENT,
                ),
                Arguments.of("WriteConflict", UncategorizedMongoDbException("x", MongoException(112, "WriteConflict")), TRANSIENT),
                Arguments.of("NotWritablePrimary", MongoException(10107, "NotWritablePrimary"), TRANSIENT),
                Arguments.of("timeout do driver", MongoTimeoutException("timeout"), TRANSIENT),
                Arguments.of("banco fora", DataAccessResourceFailureException("down"), TRANSIENT),
                Arguments.of("fora do contrato", InvalidMessageException("x"), PERMANENT),
                Arguments.of("sem handler", UnsupportedSagaEventException(SagaEventTypeEnum.ORDER_APPROVED), PERMANENT),
                Arguments.of("saga inconsistente", SagaInconsistentStateException(UUID.randomUUID(), "x"), PERMANENT),
                Arguments.of("chave duplicada no outbox", DuplicateKeyException("E11000"), PERMANENT),
                Arguments.of("falha desconhecida", IllegalStateException("x"), TRANSIENT),
            )

        private val DUPLICATE = EventErrorClass.DUPLICATE
        private val TRANSIENT = EventErrorClass.TRANSIENT
        private val PERMANENT = EventErrorClass.PERMANENT

        private fun labeled(label: String) = MongoException("erro rotulado").apply { addLabel(label) }
    }
}
