package com.fiap.mechanicalhub.orchestrator

import com.fiap.mechanicalhub.orchestrator.domain.enums.SagaStepEnum
import com.fiap.mechanicalhub.orchestrator.domain.model.Payment
import com.fiap.mechanicalhub.orchestrator.domain.model.Saga
import com.fiap.mechanicalhub.orchestrator.fixtures.SagaFixtures
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.documents.OutboxEventDocument
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.documents.TransactionHistoryDocument
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.documents.TransactionRegisterDocument
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.mappers.toDocument
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.mappers.toDomain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import java.util.UUID

/** Base dos testes de integracao do consumidor: MongoDB (replica set) + LocalStack e o handler de teste. */
@SpringBootTest
@Import(TestcontainersConfiguration::class, TestSagaEventHandlersConfiguration::class)
abstract class SagaIntegrationTest {
    @Autowired
    protected lateinit var mongoTemplate: MongoTemplate

    /** Saga gravada direto no Mongo, com `transactionId` e `orderId` novos. */
    protected fun givenSaga(
        currentStep: SagaStepEnum,
        seq: Long = 1,
        payment: Payment? = null,
    ): Saga {
        val saga =
            SagaFixtures
                .saga(currentStep = currentStep, seq = seq, payment = payment)
                .copy(transactionId = UUID.randomUUID(), orderId = UUID.randomUUID())
        mongoTemplate.insert(saga.toDocument())
        return saga
    }

    protected fun storedSaga(transactionId: UUID): Saga? =
        mongoTemplate.findById(transactionId.toString(), TransactionRegisterDocument::class.java)?.toDomain()

    protected fun historyOf(orderId: UUID): List<TransactionHistoryDocument> =
        mongoTemplate.find(Query(Criteria.where("orderId").`is`(orderId.toString())), TransactionHistoryDocument::class.java)

    protected fun outboxOf(orderId: UUID): List<OutboxEventDocument> =
        mongoTemplate
            .find(Query(Criteria.where("orderId").`is`(orderId.toString())), OutboxEventDocument::class.java)
            .sortedBy(OutboxEventDocument::seq)
}
