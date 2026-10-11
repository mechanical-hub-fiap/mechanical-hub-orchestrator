package com.fiap.mechanicalhub.orchestrator.domain.enums

/** Eventos consumidos de `mechanical-hub-orchestrator-events.fifo` (spec secao 3). `messageType` e o `type` do envelope. */
enum class SagaEventTypeEnum(
    val messageType: String,
) {
    DIAGNOSIS_COMPLETED("diagnosisCompleted"),
    STOCK_PENDING("stockPending"),
    STOCK_RESERVED("stockReserved"),
    BUDGET_GENERATED("budgetGenerated"),
    ORDER_APPROVED("orderApproved"),
    ORDER_REJECTED("orderRejected"),
    PAYMENT_LINK_GENERATED("paymentLinkGenerated"),
    PAYMENT_CONFIRMED("paymentConfirmed"),
    PAYMENT_FAILED("paymentFailed"),
    ORDER_CANCELED("orderCanceled"),
    ORDER_FINISHED("orderFinished"),
    STOCK_CONSUMED("stockConsumed"),
    STOCK_RESTORED("stockRestored"),
    ;

    companion object {
        private val BY_MESSAGE_TYPE = entries.associateBy(SagaEventTypeEnum::messageType)

        fun fromMessageType(messageType: String): SagaEventTypeEnum? = BY_MESSAGE_TYPE[messageType]
    }
}
