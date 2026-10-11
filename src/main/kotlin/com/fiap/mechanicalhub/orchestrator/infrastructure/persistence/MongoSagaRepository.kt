package com.fiap.mechanicalhub.orchestrator.infrastructure.persistence

import com.fiap.mechanicalhub.orchestrator.application.ports.SagaRepository
import com.fiap.mechanicalhub.orchestrator.domain.model.Saga
import com.fiap.mechanicalhub.orchestrator.domain.model.SagaChange
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.documents.TransactionRegisterDocument
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.mappers.toDocument
import com.fiap.mechanicalhub.orchestrator.infrastructure.persistence.mappers.toDomain
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository
import java.util.UUID

/** `transaction_register` via [MongoTemplate] (participa da transacao Spring corrente). */
@Repository
class MongoSagaRepository(
    private val mongoTemplate: MongoTemplate,
) : SagaRepository {
    override fun findById(transactionId: UUID): Saga? =
        mongoTemplate.findById(transactionId.toString(), TransactionRegisterDocument::class.java)?.toDomain()

    override fun applyChange(
        transactionId: UUID,
        change: SagaChange,
    ): Saga? =
        mongoTemplate
            .findAndModify(
                change.guard(transactionId),
                change.toUpdate(),
                FindAndModifyOptions.options().returnNew(true),
                TransactionRegisterDocument::class.java,
            )?.toDomain()

    private fun SagaChange.guard(transactionId: UUID): Query {
        val criteria =
            Criteria
                .where(ID)
                .`is`(transactionId.toString())
                .and(CURRENT_STEP)
                .`is`(expectedStep)
        if (requiresPaymentLinkAbsent) criteria.and(PAYMENT_LINK).`is`(null)
        return Query(criteria)
    }

    private fun SagaChange.toUpdate(): Update {
        val update =
            Update()
                .set(STATUS, status)
                .set(CURRENT_STEP, nextStep)
                .set(UPDATED_AT, updatedAt)
                .inc(SEQ, seqIncrement)
        finishedAt?.let { update.set(FINISHED_AT, it) }
        reservedMaterials?.let { materials -> update.set(RESERVED_MATERIALS, materials.map { it.toDocument() }) }
        budget?.let { update.set(BUDGET, it.toDocument()) }
        paymentLink?.let { update.set(PAYMENT_LINK, it) }
        paymentExpiresAt?.let { update.set(PAYMENT_EXPIRES_AT, it) }
        paymentId?.let { update.set(PAYMENT_ID, it) }
        return update
    }

    private companion object {
        const val ID = "_id"
        const val STATUS = "status"
        const val CURRENT_STEP = "currentStep"
        const val SEQ = "seq"
        const val UPDATED_AT = "updatedAt"
        const val FINISHED_AT = "finishedAt"
        const val RESERVED_MATERIALS = "reservedMaterials"
        const val BUDGET = "budget"
        const val PAYMENT_LINK = "payment.link"
        const val PAYMENT_EXPIRES_AT = "payment.expiresAt"
        const val PAYMENT_ID = "payment.paymentId"
    }
}
